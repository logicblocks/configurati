(ns configurati.sources
  (:refer-clojure :exclude [replace resolve])
  (:require
   [environ.core :as environ]
   [clojure.string :as string]
   [clj-yaml.core :as yaml]
   [medley.core :as medley])
  (:import
   [clojure.lang IHashEq ILookup]))

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
  [config-map]

  ILookup
  (valAt [_ parameter-name]
    (get config-map parameter-name))
  (valAt [_ parameter-name default]
    (get config-map parameter-name default))

  Object
  (equals [_ other]
    (and (instance? MapConfigurationSource other)
      (= config-map (.-config-map ^MapConfigurationSource other))))
  (hashCode [_]
    (.hashCode config-map))

  IHashEq
  (hasheq [_]
    (hash config-map)))

(deftype EnvConfigurationSource
  [prefix]

  ILookup
  (valAt [_ parameter-name]
    (get (read-system-env) (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (get (read-system-env) (prefix-keyword prefix parameter-name) default))

  Object
  (equals [_ other]
    (and (instance? EnvConfigurationSource other)
      (= prefix (.-prefix ^EnvConfigurationSource other))))
  (hashCode [_]
    (.hashCode prefix))

  IHashEq
  (hasheq [_]
    (hash prefix)))

(deftype EnvironConfigurationSource
  [prefix]

  ILookup
  (valAt [_ parameter-name]
    (environ/env (prefix-keyword prefix parameter-name)))
  (valAt [_ parameter-name default]
    (environ/env (prefix-keyword prefix parameter-name) default))

  Object
  (equals [_ other]
    (and (instance? EnvironConfigurationSource other)
      (= prefix (.-prefix ^EnvironConfigurationSource other))))
  (hashCode [_]
    (.hashCode prefix))

  IHashEq
  (hasheq [_]
    (hash prefix)))

(deftype YamlFileConfigurationSource
  [path prefix]

  ILookup
  (valAt [_ parameter-name]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name))))
  (valAt [_ parameter-name default]
    (let [contents (read-yaml-configuration-file path)]
      (get contents (prefix-keyword prefix parameter-name) default)))

  Object
  (equals [_ other]
    (and (instance? YamlFileConfigurationSource other)
      (= path (.-path ^YamlFileConfigurationSource other))
      (= prefix (.-prefix ^YamlFileConfigurationSource other))))
  (hashCode [_]
    (.hashCode [path prefix]))

  IHashEq
  (hasheq [_]
    (hash [path prefix])))

(deftype FnConfigurationSource
  [source-fn]

  ILookup
  (valAt [_ parameter-name]
    (source-fn parameter-name))
  (valAt [_ parameter-name default]
    (or (source-fn parameter-name) default))

  Object
  (equals [_ other]
    (and (instance? FnConfigurationSource other)
      (= source-fn (.-source-fn ^FnConfigurationSource other))))

  (hashCode [_]
    (.hashCode source-fn))

  IHashEq
  (hasheq [_]
    (hash source-fn)))

(deftype MultiConfigurationSource
  [sources]

  ILookup
  (valAt [_ parameter-name]
    (medley/find-first
      #(not (nil? %))
      (map parameter-name sources)))
  (valAt [this parameter-name default]
    (get this parameter-name default))

  Object
  (equals [_ other]
    (and (instance? MultiConfigurationSource other)
      (= sources (.-sources ^MultiConfigurationSource other))))

  (hashCode [_]
    (.hashCode sources))

  IHashEq
  (hasheq [_]
    (hash sources)))
