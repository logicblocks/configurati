(ns configurati.sources
  (:refer-clojure :exclude [replace resolve])
  (:require
   [environ.core :as environ]
   [clojure.string :as string]
   [clj-yaml.core :as yaml]
   [medley.core :as medley])
  (:import
   [clojure.lang ILookup]))

(defn- prefix-keyword [prefix k]
  (if prefix
    (keyword (string/join "-" [(name prefix) (name k)]))
    k))

(defn- ->kebab-case-keyword [value]
  (-> (name value)
    (string/lower-case)
    (string/replace "_" "-")
    (string/replace "." "-")
    (keyword)))

(defn- convert-keys-to-kebab-case [coll]
  (medley/map-keys ->kebab-case-keyword coll))

(defn- read-system-env
  []
  (->> (System/getenv)
    (map (fn [[k v]] [(->kebab-case-keyword k) v]))
    (into {})))

(defn- read-yaml-configuration-file [path]
  (->>
    (slurp path)
    (yaml/parse-string)
    (convert-keys-to-kebab-case)))

(deftype MapConfigurationSource
  [m]
  ILookup
  (valAt [_ parameter-name]
    (get m parameter-name))
  (valAt [_ parameter-name default]
    (get m parameter-name default)))

(deftype EnvConfigurationSource
  [prefix]
  ILookup
  (valAt [_ parameter-name]
    (get (read-system-env) (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (get (read-system-env) (prefix-keyword prefix parameter-name) default)))

(deftype EnvironConfigurationSource
  [prefix]
  ILookup
  (valAt [_ parameter-name]
    (environ/env (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (environ/env (prefix-keyword prefix parameter-name) default)))

(deftype YamlFileConfigurationSource
  [path prefix]
  ILookup
  (valAt [_ parameter-name]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name))))
  (valAt [_ parameter-name default]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name) default))))

(deftype FnConfigurationSource
  [source-fn]
  ILookup
  (valAt [_ parameter-name]
    (source-fn parameter-name))
  (valAt [_ parameter-name default]
    (or (source-fn parameter-name) default)))

(deftype MultiConfigurationSource
  [sources]
  ILookup
  (valAt [_ parameter-name]
    (medley/find-first
      #(not (nil? %))
      (map parameter-name sources)))
  (valAt [this parameter-name default]
    (get this parameter-name default)))
