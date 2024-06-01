(ns configurati.component-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.stuartsierra.component :as comp]
   [configurati.core :as conf]
   [configurati.component :as conf-comp]))

(defrecord NonConfigurableComponent [])

(defrecord ConfigurableComponent [configuration-definition configuration]
           conf-comp/Configurable
           (configure [component]
             (assoc component
               :configuration
               (conf/resolve configuration-definition))))

(defrecord DependentComponent
  [configurable-component configuration-selector value]
  conf-comp/Configurable
  (configure [component]
    (assoc component
      :value (configuration-selector
               (:configuration configurable-component)))))

(deftest configures-single-component-system
  (let [config-def
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-parameter :second)
          (conf/with-source
            (conf/map-source
              {:first 1 :second 2 :third 3})))
        component (map->ConfigurableComponent
                    {:configuration-definition config-def})
        initial-system (comp/system-map
                         :component component)
        configured-system (conf-comp/configure-system initial-system)]
    (is (= {:first 1 :second 2}
          (get-in configured-system [:component :configuration])))))

(deftest configures-multi-component-system
  (let [config-def-1
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-source {:first 1 :second 2}))
        config-def-2
        (conf/configuration
          (conf/with-parameter :second)
          (conf/with-source {:second 2 :third 3}))
        component-1 (map->ConfigurableComponent
                      {:configuration-definition config-def-1})
        component-2 (map->ConfigurableComponent
                      {:configuration-definition config-def-2})
        initial-system (comp/system-map
                         :component-1 component-1
                         :component-2 component-2)
        configured-system (conf-comp/configure-system initial-system)]
    (is (= {:first 1}
          (get-in configured-system [:component-1 :configuration])))
    (is (= {:second 2}
          (get-in configured-system [:component-2 :configuration])))))

(deftest configures-dependent-component-system
  (let [config-def
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-parameter :second)
          (conf/with-source {:first 1 :second 2 :third 3}))
        configurable-component
        (map->ConfigurableComponent
          {:configuration-definition config-def})
        dependent-component
        (map->DependentComponent
          {:configuration-selector (fn [config] (:second config))})
        initial-system
        (comp/system-map
          :configurable-component
          configurable-component

          :dependent-component
          (comp/using dependent-component
            [:configurable-component]))
        configured-system (conf-comp/configure-system initial-system)]
    (is (= 2 (get-in configured-system [:dependent-component :value])))))

(deftest ignores-non-configurable-components
  (let [config-def
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-source {:first 1 :second 2}))
        configurable-component
        (map->ConfigurableComponent
          {:configuration-definition config-def})
        non-configurable-component
        (map->NonConfigurableComponent {})
        initial-system
        (comp/system-map
          :configurable-component configurable-component
          :non-configurable-component non-configurable-component)
        configured-system (conf-comp/configure-system initial-system)]
    (is (= {:first 1}
          (get-in configured-system
            [:configurable-component :configuration])))
    (is (= non-configurable-component
          (get configured-system :non-configurable-component)))))

(deftest throws-on-resolution-failure-on-single-component
  (let [config-def
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-parameter :second)
          (conf/with-source
            (conf/map-source
              {:third 3})))
        config-resolution-error
        (try (conf/resolve config-def) (catch Throwable t t))
        component (map->ConfigurableComponent
                    {:configuration-definition config-def})
        initial-system (comp/system-map
                         :component component)
        configuration-error
        (try
          (conf-comp/configure-system initial-system)
          (catch Exception e e))
        configuration-error-data
        (ex-data configuration-error)]
    (is (= (str "Error during configuration of system "
             "com.stuartsierra.component.SystemMap "
             "in components #{:component}")
          (ex-message configuration-error)))
    (is (= ::conf-comp/system-configuration-threw-exception
          (:reason configuration-error-data)))
    (is (some? (:system configuration-error-data)))
    (is (= (ex-message config-resolution-error)
          (ex-message (get-in configuration-error-data
                        [:components :component]))))
    (is (= (ex-data config-resolution-error)
          (ex-data (get-in configuration-error-data
                     [:components :component]))))))

(deftest throws-on-resolution-failure-on-many-components
  (let [config-def-1
        (conf/configuration
          (conf/with-parameter :first)
          (conf/with-parameter :second)
          (conf/with-source
            (conf/map-source
              {:third 3})))
        config-def-2
        (conf/configuration
          (conf/with-parameter :second)
          (conf/with-parameter :third)
          (conf/with-source
            (conf/map-source
              {:first 1})))
        config-resolution-error-1
        (try (conf/resolve config-def-1) (catch Throwable t t))
        config-resolution-error-2
        (try (conf/resolve config-def-2) (catch Throwable t t))

        component-1 (map->ConfigurableComponent
                      {:configuration-definition config-def-1})
        component-2 (map->ConfigurableComponent
                      {:configuration-definition config-def-2})
        initial-system (comp/system-map
                         :component-1 component-1
                         :component-2 component-2)
        configuration-error
        (try
          (conf-comp/configure-system initial-system)
          (catch Exception e e))
        configuration-error-data
        (ex-data configuration-error)]
    (is (= (str "Error during configuration of system "
             "com.stuartsierra.component.SystemMap "
             "in components #{:component-1 :component-2}")
          (ex-message configuration-error)))
    (is (= ::conf-comp/system-configuration-threw-exception
          (:reason configuration-error-data)))
    (is (some? (:system configuration-error-data)))
    (is (= (ex-message config-resolution-error-1)
          (ex-message (get-in configuration-error-data
                        [:components :component-1]))))
    (is (= (ex-data config-resolution-error-1)
          (ex-data (get-in configuration-error-data
                     [:components :component-1]))))
    (is (= (ex-message config-resolution-error-2)
          (ex-message (get-in configuration-error-data
                        [:components :component-2]))))
    (is (= (ex-data config-resolution-error-2)
          (ex-data (get-in configuration-error-data
                     [:components :component-2]))))))
