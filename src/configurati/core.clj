(ns configurati.core
  (:refer-clojure :exclude [resolve merge])
  (:require
   [clojure.string :as str]
   [configurati.definition :as conf-def]
   [configurati.key-fns :as conf-kfns]
   [configurati.middleware :as conf-mdlw]
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

(defn middleware-source [delegate & middleware-fns]
  (reduce
    (fn [source middleware-fn]
      (middleware-fn source))
    delegate
    middleware-fns))

(defn parameter? [value]
  (satisfies? conf-param/Processable value))

(defn- element? [value]
  (and (sequential? value)
    (#{:specification
       :parameter
       :source
       :key-fn
       :lookup-prefix
       :transformation}
     (first value))))

(defn- element [element-type value]
  [element-type value])

(defn- flatten-elements [elements]
  (reduce
    (fn [elements element]
      (if (element? element)
        (concat elements [element])
        (concat elements element)))
    []
    elements))

(defn- collect-elements [args]
  (group-by first (flatten-elements (remove nil? args))))

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

(defn with-json-parsing
  ([]
   (with-json-parsing {}))
  ([opts]
   (with-middleware
     (conf-mdlw/json-parsing-middleware opts))))

(defn with-separator-parsing
  ([]
   (with-separator-parsing {}))
  ([opts]
   (with-middleware
     (conf-mdlw/separator-parsing-middleware opts))))

(defn with-parameter-name-transformation
  ([transform-fn]
   (with-parameter-name-transformation transform-fn {}))
  ([transform-fn opts]
   (with-middleware
     (conf-mdlw/parameter-name-transforming-middleware
       (clojure.core/merge opts
         {:transform-fn transform-fn})))))

(defn with-parameter-name-prefix
  ([prefix]
   (with-parameter-name-prefix prefix {}))
  ([prefix opts]
   (with-middleware
     (conf-mdlw/parameter-name-transforming-middleware
       (clojure.core/merge opts
         {:transform-fn (conf-kfns/add-prefix prefix)})))))

(defn with-source [source & middleware-fns]
  (element :source
    (apply middleware-source source middleware-fns)))

(defn with-lookup-prefix [prefix]
  (when prefix
    (element :lookup-prefix prefix)))

(defn with-key-fn [f]
  (element :key-fn f))

(defn with-specification [specification]
  (element :specification specification))

(defn with-transformation [transformation]
  (element :transformation transformation))

(defn from-configuration-specification [{:keys [parameters options]}]
  (concat
    (mapv (partial element :parameter) parameters)
    (when (:lookup-prefix options)
      [(element :lookup-prefix (:lookup-prefix options))])
    [(element :key-fn (:key-fn options))
     (element :transformation (:transformation options))]))

(defn configuration-specification [& args]
  (let [elements (collect-elements args)

        parameters
        (map second (:parameter elements))

        key-fns
        (reverse (map second (:key-fn elements)))
        key-fn
        (if (= (count key-fns) 1)
          (first key-fns)
          (apply comp key-fns))

        lookup-prefixes
        (map second (:lookup-prefix elements))
        lookup-prefix
        (when (seq lookup-prefixes)
          (keyword (str/join "-" (map name lookup-prefixes))))

        transformations
        (reverse (map second (:transformation elements)))
        transformation
        (if (= (count transformations) 1)
          (first transformations)
          (apply comp transformations))]
    (conf-spec/->ConfigurationSpecification
      parameters
      {:key-fn         key-fn
       :lookup-prefix  lookup-prefix
       :transformation transformation})))

(defn ^:deprecated define-configuration-specification [& args]
  (apply configuration-specification args))

(defn from-configuration [configuration]
  (concat
    (mapv (partial element :specification) (:specifications configuration))
    [(element :source (:source configuration))]))

(defn configuration [& args]
  (let [elements (collect-elements args)

        top-level-parameters
        (map second (:parameter elements))

        top-level-key-fns
        (reverse (map second (:key-fn elements)))
        top-level-key-fn
        (if (= (count top-level-key-fns) 1)
          (first top-level-key-fns)
          (apply comp top-level-key-fns))

        top-level-lookup-prefixes
        (map second (:lookup-prefix elements))
        top-level-lookup-prefix
        (when (seq top-level-lookup-prefixes)
          (keyword (str/join "-" (map name top-level-lookup-prefixes))))

        top-level-transformations
        (reverse (map second (:transformation elements)))
        top-level-transformation
        (if (= (count top-level-transformations) 1)
          (first top-level-transformations)
          (apply comp top-level-transformations))

        top-level-specification
        (conf-spec/->ConfigurationSpecification
          top-level-parameters
          {:key-fn         top-level-key-fn
           :lookup-prefix  top-level-lookup-prefix
           :transformation top-level-transformation})

        existing-specifications (map second (:specification elements))
        existing-specifications
        (map
          (fn [{:keys [parameters options]}]
            (let [{:keys [key-fn lookup-prefix transformation]} options
                  lookup-prefixes
                  (remove nil? [top-level-lookup-prefix lookup-prefix])
                  lookup-prefix
                  (when (seq lookup-prefixes)
                    (keyword (str/join "-" (map name lookup-prefixes))))]
              (conf-spec/->ConfigurationSpecification
                parameters
                {:key-fn        (comp top-level-key-fn key-fn)
                 :lookup-prefix lookup-prefix
                 :transformation
                 (comp top-level-transformation transformation)})))
          existing-specifications)

        specifications (conj existing-specifications top-level-specification)

        sources (map second (:source elements))
        source
        (if (= (count sources) 1)
          (first sources)
          (conf-sources/->MultiConfigurationSource sources))]
    (conf-def/->ConfigurationDefinition source specifications)))

(defn ^:deprecated define-configuration [& args]
  (apply configuration args))

(defn resolve [definition]
  (configurati.definition/resolve definition))

(defn merge [& definitions]
  (apply configurati.definition/merge definitions))
