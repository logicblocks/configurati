(ns configurati.key-fns
  (:refer-clojure :exclude [replace])
  (:require
    [clojure.string :refer [replace]]))

(defn remove-prefix [prefix]
  (fn [key] (keyword (replace (name key) (str (name prefix) "-") ""))))
(defn add-prefix [prefix]
  (fn [key] (keyword (str (name prefix) "-" (name key)))))
