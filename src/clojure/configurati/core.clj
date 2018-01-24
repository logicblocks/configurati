(ns configurati.core)

(def ^:private new-validation-result
  {:missing []})

(defn- missing? [parameter value]
  (and
    (nil? value)
    (not (:nilable parameter))))

(defn- mark-missing [validation-result name]
  (update-in validation-result [:missing] #(conj % name)))

(defn- determine-validation-result [parameters configuration-map]
  (reduce
    (fn [validation-result parameter]
      (let [name (:name parameter)
            value (name configuration-map)]
        (cond
          (missing? parameter value) (mark-missing validation-result name)
          :else validation-result)))
    new-validation-result
    parameters))

(defn- validation-result-empty? [validation-result]
  (empty? (:missing validation-result)))

(defprotocol ConfigurationValidator
  (validate [configuration-specification configuration-map]))

(defrecord ConfigurationSpecification [parameters]
  ConfigurationValidator
  (validate [this configuration-map]
    (let [validation-result (determine-validation-result
                              parameters
                              configuration-map)
          valid? (validation-result-empty? validation-result)]
      (if valid?
        configuration-map
        (throw (ex-info
                 "Configuration validation failed."
                 validation-result))))))

(defn configuration-specification [& parameters]
  (->ConfigurationSpecification parameters))

(defn with-parameter [name & rest]
  (let [defaults {:nilable false}
        base {:name name}
        options (apply hash-map rest)]
    (merge defaults base options)))
