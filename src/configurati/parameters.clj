(ns configurati.parameters
  (:require
   [clojure.spec.alpha :as spec]

   [configurati.conversions :refer [convert-to]]))

(defn- invalid-reason [parameter value]
  (if-let [spec (:spec parameter)]
    (spec/explain-data spec value)
    nil))

(defn- invalid? [parameter value]
  (if-let [spec (:spec parameter)]
    (not (spec/valid? spec value))
    false))

(defn- missing? [parameter value]
  (and

    (nil? value)
    (not (:nilable parameter))))

(defprotocol Processable
  (validate [parameter value])
  (default [parameter value])
  (convert [parameter value]))

(defrecord ConfigurationParameter
  [name nilable default type spec]
  Processable
  (validate [this value]
    (cond
      (missing? this value) {:error :missing
                             :value value}
      (invalid? this value) {:error  :invalid
                             :value  value
                             :reason (invalid-reason this value)}
      :else {:error nil
             :value value}))
  (default [_ value]
    (or value default))
  (convert [_ value]
    (try
      {:error nil :value (convert-to type value)}
      (catch Exception _
        {:error :unconvertible :value nil}))))
