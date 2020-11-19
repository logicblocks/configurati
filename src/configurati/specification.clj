(ns configurati.specification
  (:require
   [configurati.parameters :refer [default validate convert]]))

(defprotocol Evaluatable
  (evaluate [configuration-specification configuration-source]))

(defn- evaluation-result [configuration-source]
  {:missing       []
   :invalid       []
   :unconvertible []
   :reasons       {}
   :original      configuration-source
   :evaluated     {}})

(defn- error-free? [evaluation-result]
  (and
    (empty? (:missing evaluation-result))
    (empty? (:invalid evaluation-result))
    (empty? (:unconvertible evaluation-result))))

(defn- with-error [evaluation-result parameter-name error]
  (let [error-type (:error error)
        reason (:reason error)]
    (if reason
      (-> evaluation-result
        (update-in [error-type] #(conj % parameter-name))
        (update-in [:reasons] #(merge % {parameter-name reason})))
      (update-in evaluation-result [error-type] #(conj % parameter-name)))))

(defn- with-value [evaluation-result parameter-name value]
  (update-in evaluation-result [:evaluated] #(assoc % parameter-name value)))

(defn- determine-evaluation-result [parameters configuration-source key-fn]
  (reduce
    (fn [evaluation-result parameter]
      (let [parameter-name (:name parameter)
            initial (parameter-name configuration-source)
            defaulted (default parameter initial)
            validity (validate parameter defaulted)
            conversion (convert parameter defaulted)
            converted (:value conversion)
            validation-error (select-keys validity [:error :reason])
            conversion-error (select-keys conversion [:error])]
        (cond
          (:error validation-error)
          (with-error evaluation-result parameter-name validation-error)

          (:error conversion-error)
          (with-error evaluation-result parameter-name conversion-error)

          :default
          (with-value evaluation-result (key-fn parameter-name) converted))))
    (evaluation-result configuration-source)
    parameters))

(defrecord ConfigurationSpecification
  [parameters key-fn]
  Evaluatable
  (evaluate [_ configuration-source]
    (let [evaluation-result (determine-evaluation-result
                              parameters
                              configuration-source
                              key-fn)
          valid? (error-free? evaluation-result)
          missing-parameters (:missing evaluation-result)
          invalid-parameters (:invalid evaluation-result)
          unconvertible-parameters (:unconvertible evaluation-result)]
      (if valid?
        (:evaluated evaluation-result)
        (throw (ex-info
                 (str "Configuration evaluation failed. "
                   "Missing parameters: " missing-parameters ", "
                   "invalid parameters: " invalid-parameters ", "
                   "unconvertible parameters: " unconvertible-parameters ".")
                 evaluation-result))))))
