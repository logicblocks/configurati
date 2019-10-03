(ns configurati.core
  (:refer-clojure :exclude [resolve])
  (:require
    [configurati.definition
     :refer [->ConfigurationDefinition]]
    [configurati.parameters
     :refer [map->ConfigurationParameter]]
    [configurati.sources
     :refer [->MapConfigurationSource
             ->EnvConfigurationSource
             ->YamlFileConfigurationSource
             ->MultiConfigurationSource]]
    [configurati.specification
     :refer [->ConfigurationSpecification]]))

(defn map-source [map]
  (->MapConfigurationSource map))

(defn env-source [& rest]
  (let [options (apply hash-map rest)
        prefix (:prefix options)]
    (->EnvConfigurationSource prefix)))

(defn yaml-file-source [path & rest]
  (let [options (apply hash-map rest)
        prefix (:prefix options)]
    (->YamlFileConfigurationSource path prefix)))

(defn multi-source [& sources]
  (->MultiConfigurationSource sources))

(defn with-parameter [name & rest]
  (let [defaults {:nilable false
                  :type    :string}
        base {:name name}
        options (apply hash-map rest)]
    [:parameter (map->ConfigurationParameter
                  (merge defaults base options))]))

(defn with-source [source]
  [:source source])

(defn with-key-fn [fn]
  [:key-fn fn])

(defn with-specification [specification]
  [:specification specification])

(defn define-configuration-specification [& rest]
  (let [elements (group-by #(first %) rest)
        parameters (map second (:parameter elements))
        key-fn (apply comp (map second (:key-fn elements)))]
    (->ConfigurationSpecification parameters key-fn)))

(defn define-configuration [& rest]
  (let [elements (group-by #(first %) rest)

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