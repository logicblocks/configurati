(ns configurati.specification
  (:require
    [configurati.parameters :refer [default validate convert]]))

(defprotocol Evaluatable
  (evaluate [configuration-specification configuration-source]))

(defn- evaluation-result [configuration-source]
  {:missing       []
   :unconvertible []
   :original      configuration-source
   :evaluated     {}})

(defn- error-free? [evaluation-result]
  (and
    (empty? (:missing evaluation-result))
    (empty? (:unconvertible evaluation-result))))

(defn- with-error [evaluation-result name error-type]
  (update-in evaluation-result [error-type] #(conj % name)))

(defn- with-value [evaluation-result name value]
  (update-in evaluation-result [:evaluated] #(assoc % name value)))

(defn- determine-evaluation-result [parameters configuration-source key-fn]
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
          :default (with-value evaluation-result (key-fn name) converted))))
    (evaluation-result configuration-source)
    parameters))

(defrecord ConfigurationSpecification [parameters key-fn]
  Evaluatable
  (evaluate [_ configuration-source]
    (let [evaluation-result (determine-evaluation-result
                              parameters
                              configuration-source
                              key-fn)
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
