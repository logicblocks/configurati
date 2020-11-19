(ns configurati.sources
  (:refer-clojure :exclude [replace resolve])
  (:require
   [environ.core :refer [env]]
   [clojure.string :refer [join lower-case replace]]
   [clj-yaml.core :as yaml]
   [medley.core :refer [map-keys find-first]])
  (:import
   [clojure.lang ILookup]))

(defn- prefix-keyword [prefix k]
  (if prefix
    (keyword (join "-" [(name prefix) (name k)]))
    k))

(defn- to-kebab-case-keyword [value]
  (-> (name value)
    (lower-case)
    (replace "_" "-")
    (replace "." "-")
    (keyword)))

(defn- convert-keys-to-kebab-case [coll]
  (map-keys to-kebab-case-keyword coll))

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
    (env (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (env (prefix-keyword prefix parameter-name) default)))

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
    (find-first
      #(not (nil? %))
      (map parameter-name sources)))
  (valAt [this parameter-name default]
    (get this parameter-name default)))
