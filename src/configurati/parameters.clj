(ns configurati.parameters
  (:require
   [configurati.conversions :refer [convert-to]]))

(defn- missing? [parameter value]
  (and
    (nil? value)
    (not (:nilable parameter))))

(defprotocol Processable
  (validate [parameter value])
  (default [parameter value])
  (convert [parameter value]))

(defrecord ConfigurationParameter
  [name nilable default type]
  Processable
  (validate [this value]
    (cond
      (missing? this value) {:error :missing :value value}
      :else {:error nil :value value}))
  (default [_ value]
    (or value default))
  (convert [_ value]
    (try
      {:error nil :value (convert-to type value)}
      (catch Exception _
        {:error :unconvertible :value nil}))))
