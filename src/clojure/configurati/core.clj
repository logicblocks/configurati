(ns configurati.core)

; what to do with superfluous map entries?

(defprotocol Evaluatable
  (evaluate [configuration-specification configuration-map]))

(defprotocol Processable
  (validate [parameter value])
  (default [parameter value]))

(defn- evaluation-result [configuration-map]
  {:missing   []
   :original  configuration-map
   :evaluated {}})

(defn- missing? [parameter value]
  (and
    (nil? value)
    (not (:nilable parameter))))

(defn- has-errors? [evaluation-result]
  (empty? (:missing evaluation-result)))

(defn- with-error [evaluation-result name error-type]
  (update-in evaluation-result [error-type] #(conj % name)))

(defn- with-value [evaluation-result name value]
  (update-in evaluation-result [:evaluated] #(assoc % name value)))

(defn- determine-evaluation-result [parameters configuration-map]
  (reduce
    (fn [evaluation-result parameter]
      (let [name (:name parameter)
            initial (name configuration-map)
            defaulted (default parameter initial)
            validity (validate parameter defaulted)
            error (:error validity)]
        (if error
          (with-error evaluation-result name error)
          (with-value evaluation-result name defaulted))))
    (evaluation-result configuration-map)
    parameters))

(defrecord ConfigurationSpecification [parameters]
  Evaluatable
  (evaluate [this configuration-map]
    (let [evaluation-result (determine-evaluation-result
                              parameters
                              configuration-map)
          valid? (has-errors? evaluation-result)]
      (if valid?
        (:evaluated evaluation-result)
        (throw (ex-info
                 "Configuration evaluation failed."
                 evaluation-result))))))

(defrecord ConfigurationParameter [name nilable default]
  Processable
  (validate [this value]
    (cond
      (missing? this value) {:error :missing :value value}
      :else {:error nil :value value}))
  (default [this value]
    (or value default)))

(defn configuration-specification [& parameters]
  (->ConfigurationSpecification parameters))

(defn with-parameter [name & rest]
  (let [defaults {:nilable false}
        base {:name name}
        options (apply hash-map rest)]
    (map->ConfigurationParameter
      (merge defaults base options))))
