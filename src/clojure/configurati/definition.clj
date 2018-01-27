(ns configurati.definition
  (:refer-clojure :exclude [resolve])
  (:require
    [configurati.specification :refer [evaluate]]))

(defprotocol Resolvable
  (resolve [definition]))

(defrecord ConfigurationDefinition [source specification]
  Resolvable
  (resolve [this]
    (evaluate specification source)))
