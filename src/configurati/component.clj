(ns configurati.component
  (:require
   [clojure.string :as string]
   [com.stuartsierra.component :as comp]
   [com.stuartsierra.component.platform :as comp-platform]))

(defprotocol Configurable
  (configure [component opts]))

(defn- attempt-configure-system [system opts]
  (comp/update-system system (keys system)
    (fn [component]
      (if (satisfies? Configurable component)
        (try
          (configure component opts)
          (catch Throwable t
            (assoc component
              ::configuration-error t)))
        component))))

(defn- collect-configuration-errors [system]
  (reduce-kv
    (fn [error-context key component]
      (let [error (::configuration-error component)]
        (if error (assoc error-context key error) error-context)))
    nil
    system))

(defn configure-system
  ([system]
   (configure-system system {}))
  ([system opts]
   (let [configured-system
         (attempt-configure-system system opts)
         component-errors
         (collect-configuration-errors configured-system)]
     (if component-errors
       (throw
         (ex-info
           (str "Error during configuration of system "
             (comp-platform/type-name system)
             " in components #{"
             (string/join " " (vec (keys component-errors)))
             "}")
           {:reason     ::system-configuration-threw-exception
            :system     configured-system
            :components component-errors}))
       configured-system))))
