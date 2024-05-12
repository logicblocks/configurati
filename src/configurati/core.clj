(ns configurati.core
  (:refer-clojure :exclude [resolve merge])
  (:require
   [configurati.definition :as conf-def]
   [configurati.parameters :as conf-param]
   [configurati.sources :as conf-sources]
   [configurati.specification :as conf-spec]))

(defn map-source [m]
  (conf-sources/->MapConfigurationSource m))

(defn env-source [& {:as options}]
  (let [prefix (:prefix options)]
    (conf-sources/->EnvConfigurationSource prefix)))

(defn environ-source [& {:as options}]
  (let [prefix (:prefix options)]
    (conf-sources/->EnvironConfigurationSource prefix)))

(defn yaml-file-source [path & {:as options}]
  (let [prefix (:prefix options)]
    (conf-sources/->YamlFileConfigurationSource path prefix)))

(defn multi-source [& sources]
  (conf-sources/->MultiConfigurationSource sources))

(defn with-parameter [parameter-name & {:as options}]
  (let [defaults {:nilable false
                  :type    :any}
        base {:name parameter-name}]
    [:parameter (conf-param/map->ConfigurationParameter
                  (clojure.core/merge defaults base options))]))

(defn with-middleware [middleware]
  (fn [source]
    (conf-sources/->FnConfigurationSource (partial middleware source))))

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

(defn with-transformation [transformation]
  [:transformation transformation])

(defn define-configuration-specification [& args]
  (let [elements
        (group-by first args)
        parameters
        (map second (:parameter elements))
        key-fn
        (apply comp (reverse (map second (:key-fn elements))))
        transformation
        (apply comp (reverse (map second (:transformation elements))))]
    (conf-spec/->ConfigurationSpecification
      parameters key-fn transformation)))

(defn define-configuration [& args]
  (let [elements (group-by first args)

        top-level-parameters
        (map second (:parameter elements))
        top-level-key-fns
        (reverse (map second (:key-fn elements)))
        top-level-key-fn
        (apply comp top-level-key-fns)
        top-level-transformations
        (reverse (map second (:transformation elements)))
        top-level-transformation
        (apply comp top-level-transformations)

        top-level-specification
        (conf-spec/->ConfigurationSpecification
          top-level-parameters top-level-key-fn top-level-transformation)

        existing-specifications (map second (:specification elements))
        existing-specifications
        (map (fn [{:keys [parameters key-fn transformation]}]
               (conf-spec/->ConfigurationSpecification
                 parameters
                 (comp top-level-key-fn key-fn)
                 (comp top-level-transformation transformation)))
          existing-specifications)

        specifications (conj existing-specifications top-level-specification)

        sources (map second (:source elements))
        source (conf-sources/->MultiConfigurationSource sources)]
    (conf-def/->ConfigurationDefinition source specifications)))

(defn resolve [definition]
  (configurati.definition/resolve definition))

(defn merge [& definitions]
  (apply configurati.definition/merge definitions))