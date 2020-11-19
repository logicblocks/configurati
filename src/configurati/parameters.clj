(ns configurati.parameters
  (:require
   [clojure.spec.alpha :as spec]

   [configurati.conversions :refer [convert-to]]))

(defn- invalid-reason [parameter value]
  (if-let [validator (:validator parameter)]
    (spec/explain-data validator value)
    nil))

(defn- invalid? [parameter value]
  (if-let [validator (:validator parameter)]
    (not (spec/valid? validator value))
    false))

(defn- missing? [parameter value]
  (and

    (nil? value)
    (not (:nilable parameter))))

(defprotocol Processable
  (check [parameter value])
  (default [parameter value])
  (convert [parameter value])
  (validate [parameter value]))

(defrecord ConfigurationParameter
  [name nilable default type validator]
  Processable
  (check [this value]
    (cond
      (missing? this value) {:error :missing
                             :value value}
      :else {:error nil
             :value value}))
  (default [_ value]
    (or value default))
  (convert [_ value]
    (try
      {:error nil :value (convert-to type value)}
      (catch Exception _
        {:error :unconvertible :value nil})))
  (validate [this value]
    (cond
      (invalid? this value) {:error  :invalid
                             :value  value
                             :reason (invalid-reason this value)}
      :else {:error nil
             :value value})))
