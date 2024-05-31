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

(defn parameter? [value]
  (satisfies? conf-param/Processable value))

(defn- element [element-type value]
  [element-type value])

(defn parameter
  ([parameter-name]
   (parameter parameter-name {}))
  ([parameter-name options]
   (let [defaults {:nilable false
                   :type    :any}
         base {:name parameter-name}]
     (conf-param/map->ConfigurationParameter
       (clojure.core/merge defaults base options)))))

(defn with-parameter [parameter-element-or-name & {:as options}]
  (element :parameter
    (if (parameter? parameter-element-or-name)
      parameter-element-or-name
      (parameter parameter-element-or-name options))))

(defn with-middleware [middleware]
  (fn [source]
    (conf-sources/->FnConfigurationSource (partial middleware source))))

(defn with-source [source & middleware-fns]
  (element :source
    (reduce
      (fn [source middleware-fn]
        (middleware-fn source))
      source
      middleware-fns)))

(defn with-key-fn [f]
  (element :key-fn f))

(defn with-specification [specification]
  (element :specification specification))

(defn with-transformation [transformation]
  (element :transformation transformation))

(defn configuration-specification [& args]
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

(defn configuration [& args]
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

(defn ^:deprecated define-configuration-specification [& args]
  (apply configuration-specification args))

(defn ^:deprecated define-configuration [& args]
  (apply configuration args))

(defn resolve [definition]
  (configurati.definition/resolve definition))

(defn merge [& definitions]
  (apply configurati.definition/merge definitions))
