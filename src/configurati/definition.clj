(ns configurati.definition
  (:refer-clojure :exclude [resolve merge])
  (:require
   [configurati.specification :refer [evaluate]]))

(defprotocol Resolvable
  (resolve [definition]))

(defrecord ConfigurationDefinition
  [source specifications]
  Resolvable
  (resolve [_]
    (apply clojure.core/merge (map #(evaluate % source) specifications))))

(defrecord MergedConfigurationDefinition
  [definitions]
  Resolvable
  (resolve [_]
    (apply clojure.core/merge (map resolve definitions))))

(defn merge [& definitions]
  (->MergedConfigurationDefinition definitions))
