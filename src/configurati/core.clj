(ns configurati.core
  (:refer-clojure :exclude [resolve merge])
  (:require
   [configurati.definition
    :refer [->ConfigurationDefinition]]
   [configurati.parameters
    :refer [map->ConfigurationParameter]]
   [configurati.sources
    :refer [->MapConfigurationSource
            ->EnvConfigurationSource
            ->YamlFileConfigurationSource
            ->FnConfigurationSource
            ->MultiConfigurationSource]]
   [configurati.specification
    :refer [->ConfigurationSpecification]]))

(defn map-source [m]
  (->MapConfigurationSource m))

(defn env-source [& {:as options}]
  (let [prefix (:prefix options)]
    (->EnvConfigurationSource prefix)))

(defn yaml-file-source [path & {:as options}]
  (let [prefix (:prefix options)]
    (->YamlFileConfigurationSource path prefix)))

(defn multi-source [& sources]
  (->MultiConfigurationSource sources))

(defn with-parameter [parameter-name & args]
  (let [defaults {:nilable false
                  :type    :string}
        base {:name parameter-name}
        options (apply hash-map args)]
    [:parameter (map->ConfigurationParameter
                  (clojure.core/merge defaults base options))]))

(defn with-middleware [middleware]
  (fn [source]
    (->FnConfigurationSource (partial middleware source))))

(defn with-source
  ([source] [:source source])
  ([source & middleware-fns]
   [:source (reduce
              (fn [source middleware-fn]
                (middleware-fn source))
              source
              middleware-fns)]))

(defn with-key-fn [f]
  [:key-fn f])

(defn with-specification [specification]
  [:specification specification])

(defn define-configuration-specification [& args]
  (let [elements (group-by first args)
        parameters (map second (:parameter elements))
        key-fn (apply comp (map second (:key-fn elements)))]
    (->ConfigurationSpecification parameters key-fn)))

(defn define-configuration [& args]
  (let [elements (group-by first args)

        top-level-parameters (map second (:parameter elements))
        top-level-key-fns (map second (:key-fn elements))
        top-level-key-fn (apply comp top-level-key-fns)

        top-level-specification
        (->ConfigurationSpecification
          top-level-parameters top-level-key-fn)

        existing-specifications (map second (:specification elements))
        existing-specifications
        (map (fn [{:keys [parameters key-fn]}]
               (->ConfigurationSpecification
                 parameters (comp top-level-key-fn key-fn)))
          existing-specifications)

        specifications (conj existing-specifications top-level-specification)

        sources (map second (:source elements))
        source (->MultiConfigurationSource sources)]
    (->ConfigurationDefinition source specifications)))

(defn resolve [definition]
  (configurati.definition/resolve definition))

(defn merge [& definitions]
  (apply configurati.definition/merge definitions))