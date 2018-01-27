(ns configurati.core
  (:refer-clojure :exclude [replace resolve])
  (:require
    [environ.core :refer [env]]
    [clojure.string :refer [join lower-case replace]]
    [clj-yaml.core :as yaml]
    [medley.core :refer [map-keys find-first]])
  (:import [clojure.lang ILookup]))

(defprotocol Evaluatable
  (evaluate [configuration-specification configuration-source]))

(defprotocol Processable
  (validate [parameter value])
  (default [parameter value])
  (convert [parameter value]))

(defprotocol Resolvable
  (resolve [definition]))

(defn- evaluation-result [configuration-source]
  {:missing       []
   :unconvertible []
   :original      configuration-source
   :evaluated     {}})

(defn- missing? [parameter value]
  (and
    (nil? value)
    (not (:nilable parameter))))

(defn- error-free? [evaluation-result]
  (and
    (empty? (:missing evaluation-result))
    (empty? (:unconvertible evaluation-result))))

(defn- with-error [evaluation-result name error-type]
  (update-in evaluation-result [error-type] #(conj % name)))

(defn- with-value [evaluation-result name value]
  (update-in evaluation-result [:evaluated] #(assoc % name value)))

(defn- determine-evaluation-result [parameters configuration-source]
  (reduce
    (fn [evaluation-result parameter]
      (let [name (:name parameter)
            initial (name configuration-source)
            defaulted (default parameter initial)
            validity (validate parameter defaulted)
            conversion (convert parameter defaulted)
            converted (:value conversion)
            validation-error (:error validity)
            conversion-error (:error conversion)]
        (cond
          validation-error (with-error evaluation-result name validation-error)
          conversion-error (with-error evaluation-result name conversion-error)
          :default (with-value evaluation-result name converted))))
    (evaluation-result configuration-source)
    parameters))

(defmulti convert-to (fn [type value] type))
(defmethod convert-to :integer [_ value]
  (if value (Integer/parseInt (str value)) nil))
(defmethod convert-to :string [_ value]
  (if value (String/valueOf value) nil))
(defmethod convert-to :default [_ value]
  value)

(defrecord ConfigurationSpecification [parameters]
  Evaluatable
  (evaluate [this configuration-source]
    (let [evaluation-result (determine-evaluation-result
                              parameters
                              configuration-source)
          valid? (error-free? evaluation-result)
          missing-parameters (:missing evaluation-result)
          unconvertible-parameters (:unconvertible evaluation-result)]
      (if valid?
        (:evaluated evaluation-result)
        (throw (ex-info
                 (str "Configuration evaluation failed. "
                   "Missing parameters: " missing-parameters ", "
                   "unconvertible parameters: " unconvertible-parameters ".")
                 evaluation-result))))))

(defrecord ConfigurationParameter [name nilable default as]
  Processable
  (validate [this value]
    (cond
      (missing? this value) {:error :missing :value value}
      :else {:error nil :value value}))
  (default [this value]
    (or value default))
  (convert [this value]
    (try
      {:error nil :value (convert-to as value)}
      (catch Exception _
        {:error :unconvertible :value nil}))))

(defn configuration-specification [& parameters]
  (let [parameter-set (map :parameter parameters)]
    (->ConfigurationSpecification parameter-set)))

(defn with-parameter [name & rest]
  (let [defaults {:nilable false
                  :as      :string}
        base {:name name}
        options (apply hash-map rest)]
    {:parameter (map->ConfigurationParameter
                  (merge defaults base options))}))

(deftype MapConfigurationSource [map]
  ILookup
  (valAt [_ parameter-name]
    (get map parameter-name))
  (valAt [_ parameter-name default]
    (get map parameter-name default)))

(defn map-source [map]
  (->MapConfigurationSource map))

(defn- prefix-keyword [prefix key]
  (if prefix
    (keyword (join "-" [(name prefix) (name key)]))
    key))

(deftype EnvConfigurationSource [prefix]
  ILookup
  (valAt [_ parameter-name]
    (env (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (env (prefix-keyword prefix parameter-name) default)))

(defn env-source [& rest]
  (let [options (apply hash-map rest)
        prefix (:prefix options)]
    (->EnvConfigurationSource prefix)))

(defn- to-kebab-case-keyword [value]
  (-> (name value)
    (lower-case)
    (replace "_" "-")
    (replace "." "-")
    (keyword)))

(defn- convert-keys-to-kebab-case [coll]
  (map-keys to-kebab-case-keyword coll))

(defn- read-yaml-configuration-file [path]
  (->>
    (slurp path)
    (yaml/parse-string)
    (convert-keys-to-kebab-case)))

(deftype YamlFileConfigurationSource [path prefix]
  ILookup
  (valAt [_ parameter-name]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name))))
  (valAt [_ parameter-name default]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name) default))))

(defn yaml-file-source [path & rest]
  (let [options (apply hash-map rest)
        prefix (:prefix options)]
    (->YamlFileConfigurationSource path prefix)))

(deftype MultiConfigurationSource [sources]
  ILookup
  (valAt [_ parameter-name]
    (find-first
      #(not (nil? %))
      (map parameter-name sources)))
  (valAt [this parameter-name default]
    (get this parameter-name default)))

(defn multi-source [& sources]
  (->MultiConfigurationSource sources))

(defn with-source [source]
  {:source source})

(defrecord ConfigurationDefinition [source specification]
  Resolvable
  (resolve [this]
    (evaluate specification source)))

(defn- add-to [result type value]
  (update-in result [type] #(conj % value)))

(defn define-configuration [& rest]
  (let [elements (reduce
                   (fn [result item]
                     (let [type (first (keys item))
                           value (first (vals item))]
                       (cond
                         (= :source type) (add-to result :sources value)
                         (= :parameter type) (add-to result :parameters value)
                         :else result)))
                   {:parameters []
                    :sources    []}
                   rest)
        parameters (:parameters elements)
        sources (:sources elements)]
    (->ConfigurationDefinition
      (->MultiConfigurationSource sources)
      (->ConfigurationSpecification parameters))))
