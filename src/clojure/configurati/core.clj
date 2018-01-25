(ns configurati.core)

; what to do with superfluous map entries?

(defprotocol Evaluatable
  (evaluate [configuration-specification configuration-map]))

(defprotocol Processable
  (validate [parameter value])
  (default [parameter value])
  (convert [parameter value]))

(defn- evaluation-result [configuration-map]
  {:missing       []
   :unconvertible []
   :original      configuration-map
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

(defn- determine-evaluation-result [parameters configuration-map]
  (reduce
    (fn [evaluation-result parameter]
      (let [name (:name parameter)
            initial (name configuration-map)
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
    (evaluation-result configuration-map)
    parameters))

(defmulti convert-to (fn [type value] type))
(defmethod convert-to :integer [_ value]
  (if value
    (Integer/parseInt value)
    nil))
(defmethod convert-to :string [_ value]
  (if value
    (String/valueOf value)
    nil))
(defmethod convert-to :default [_ value]
  value)

(defrecord ConfigurationSpecification [parameters]
  Evaluatable
  (evaluate [this configuration-map]
    (let [evaluation-result (determine-evaluation-result
                              parameters
                              configuration-map)
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
  (->ConfigurationSpecification parameters))

(defn with-parameter [name & rest]
  (let [defaults {:nilable false
                  :as      :string}
        base {:name name}
        options (apply hash-map rest)]
    (map->ConfigurationParameter
      (merge defaults base options))))
