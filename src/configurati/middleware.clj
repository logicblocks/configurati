(ns configurati.middleware
  (:require
   [clojure.string :as string]
   [cheshire.core :as json]))

(defn- re-quote [s]
  (let [special-characters (set ".?*+^$[]\\(){}|")
        escape-fn #(if (special-characters %) (str \\ %) %)]
    (clojure.string/join (map escape-fn s))))

(defn json-parsing-middleware
  ([] (json-parsing-middleware {}))
  ([opts]
   (fn [source parameter-name]
     (let [key-fn (get opts :key-fn true)
           parse-fn (get opts :parse-fn
                      (fn [value] (json/parse-string value key-fn)))
           parameters-to-parse (set (get opts :only))
           parameter-value (get source parameter-name)]
       (if (or (empty? parameters-to-parse)
             (parameters-to-parse parameter-name))
         (parse-fn parameter-value)
         parameter-value)))))

(defn separator-parsing-middleware
  ([] (separator-parsing-middleware {}))
  ([opts]
   (fn [source parameter-name]
     (let [separator (get opts :separator ",")
           trim (get opts :trim true)
           parse-fn
           (get opts :parse-fn
             (fn [value]
               (if value
                 (let [parts (string/split value
                               (re-pattern (re-quote separator)))
                       trimmed (if trim (map #(string/trim %) parts) parts)]
                   trimmed)
                 value)))
           parameters-to-parse (set (get opts :only))
           parameter-value (get source parameter-name)]
       (if (or (empty? parameters-to-parse)
             (parameters-to-parse parameter-name))
         (parse-fn parameter-value)
         parameter-value)))))
