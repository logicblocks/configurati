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
