(ns configurati.core-test
  (:refer-clojure :exclude [replace resolve])
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.test :refer [deftest is testing]]

   [configurati.conversions :as conf-conv]
   [configurati.core :as conf]
   [configurati.key-fns :as conf-kf]
   [configurati.parameters :as conf-param]
   [configurati.specification :as conf-spec])
  (:import
   [clojure.lang ExceptionInfo]))

(defmethod conf-conv/convert-to :boolean [_ value]
  (if (#{"true" true} value) true false))

(deftest configuration-parameters
  (testing "construction"
    (is (= (conf-param/map->ConfigurationParameter
             {:name    :api-username
              :nilable false
              :default nil
              :type    :any})
          (conf/parameter :api-username)))
    (is (= (conf-param/map->ConfigurationParameter
             {:name    :api-username
              :nilable false
              :default nil
              :type    :any})
          (conf/parameter :api-username {:nilable false})))
    (is (= (conf-param/map->ConfigurationParameter
             {:name    :api-username
              :nilable true
              :default nil
              :type    :any})
          (conf/parameter :api-username {:nilable true})))
    (is (= (conf-param/map->ConfigurationParameter
             {:name    :api-username
              :nilable false
              :default "username"
              :type    :any})
          (conf/parameter :api-username {:default "username"})))
    (is (= (conf-param/map->ConfigurationParameter
             {:name    :api-port
              :nilable false
              :default nil
              :type    :integer})
          (conf/parameter :api-port {:type :integer}))))

  (testing "defaulting"
    (let [parameter (conf-param/map->ConfigurationParameter
                      {:name    :api-password
                       :nilable false
                       :default "P@55w0rd"})]
      (is (= "P@55w0rd" (conf-param/default parameter nil))))
    (let [parameter (conf-param/map->ConfigurationParameter
                      {:name    :api-password
                       :nilable false
                       :default nil})]
      (is (= nil (conf-param/default parameter nil)))))

  (testing "checking"
    (let [parameter (conf-param/map->ConfigurationParameter
                      {:name :api-username :nilable false :default nil})]
      (is (= {:error :missing :value nil}
            (conf-param/check parameter nil)))
      (is (= {:error nil :value "username"}
            (conf-param/check parameter "username")))))

  (testing "conversion"
    (let [parameter (conf-param/map->ConfigurationParameter
                      {:name    :api-username
                       :nilable false
                       :default nil
                       :type    :integer})]
      (is (= {:error nil :value 5000}
            (conf-param/convert parameter "5000")))
      (is (= {:error :unconvertible :value nil}
            (conf-param/convert parameter "abcd")))))

  (testing "validation"
    (let [validator (fn [value] (>= (count value) 8))
          parameter (conf-param/map->ConfigurationParameter
                      {:name :api-username :validator validator})]
      (is (= {:error  :invalid
              :value  "user"
              :reason (spec/explain-data validator "user")}
            (conf-param/validate parameter "user")))
      (is (= {:error nil
              :value "username"}
            (conf-param/validate parameter "username"))))))

(deftest configuration-specifications
  (testing "construction"
    (testing "allows adding predefined parameter"
      (let [parameter-element (conf/parameter :api-username {:nilable false})]
        (is (= (conf/configuration-specification
                 (conf/with-parameter parameter-element))
              (conf/configuration-specification
                (conf/with-parameter :api-username :nilable false))))))

    (testing "allows copying from existing configuration specification"
      (let [key-fn (conf-kf/remove-prefix :some)
            transformation
            (fn [config]
              (assoc config :api-description "Some API"))
            existing-specification
            (conf/configuration-specification
              (conf/with-key-fn key-fn)
              (conf/with-parameter :some-api-username)
              (conf/with-transformation transformation))
            new-specification
            (conf/configuration-specification
              (conf/from-configuration-specification existing-specification)
              (conf/with-parameter :api-password))]
        (is (= {:api-username "admin"
                :api-password "super-secret"
                :api-description "Some API"}
              (conf/resolve
                (conf/configuration
                  (conf/with-specification new-specification)
                  (conf/with-source
                    (conf/map-source
                      {:some-api-username "admin"
                       :api-password "super-secret"})))))))))

  (testing "evaluate"
    (let [evaluate-and-catch
          (fn [specification configuration-source]
            (try
              (conf-spec/evaluate specification configuration-source)
              (catch ExceptionInfo e e)))]
      (testing "returns configuration map when no errors occur"
        (let [specification
              (conf/configuration-specification
                (conf/with-parameter :api-username :nilable false)
                (conf/with-parameter :api-password :nilable false))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= configuration-source
                (conf-spec/evaluate specification configuration-source)))))

      (testing "throws exception when non-nilable parameter is nil"
        (let [specification
              (conf/configuration-specification
                (conf/with-parameter :api-username :nilable false)
                (conf/with-parameter :api-password :nilable false)
                (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username nil
                                    :api-password "some-password"
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [:api-username], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       [:api-username]
                  :invalid       []
                  :unconvertible []
                  :reasons       {}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-password :api-group])}
                (ex-data exception)))))

      (testing "throws exception when parameter does not match spec"
        (let [validator (fn [value] (>= (count value) 8))
              specification
              (conf/configuration-specification
                (conf/with-parameter :api-username :validator validator)
                (conf/with-parameter :api-password :nilable false)
                (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username "user"
                                    :api-password "some-password"
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "invalid parameters: [:api-username], "
                   "unconvertible parameters: [].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       []
                  :invalid       [:api-username]
                  :unconvertible []
                  :reasons       {:api-username
                                  (spec/explain-data validator "user")}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-password :api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all missing parameters when "
                 "multiple non-nilable parameters are nil")
        (let [specification
              (conf/configuration-specification
                (conf/with-parameter :api-username :nilable false)
                (conf/with-parameter :api-password :nilable false)
                (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username nil
                                    :api-password nil
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [:api-username :api-password], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       [:api-username
                                  :api-password]
                  :invalid       []
                  :unconvertible []
                  :reasons       {}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all invalid parameters when "
                 "multiple do not match spec")
        (let [validator (fn [value] (>= (count value) 8))
              specification
              (conf/configuration-specification
                (conf/with-parameter :api-username :validator validator)
                (conf/with-parameter :api-password :validator validator)
                (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username "user"
                                    :api-password "pass"
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "invalid parameters: [:api-username :api-password], "
                   "unconvertible parameters: [].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       []
                  :invalid       [:api-username
                                  :api-password]
                  :unconvertible []
                  :reasons       {:api-username
                                  (spec/explain-data validator "user")
                                  :api-password
                                  (spec/explain-data validator "pass")}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing "returns provided default when parameter is nil"
        (let [default-identifier "default-identifier"
              specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username   "some-username"
                                    :api-password   "some-password"
                                    :api-identifier nil}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (conf-spec/evaluate specification configuration-source)))))

      (testing "returns provided boolean override when default boolean true"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :thing :default true))
              configuration-source {:thing false}]
          (is (= configuration-source
                (conf-spec/evaluate specification configuration-source)))))

      (testing "returns provided default when parameter is not present"
        (let [default-identifier "default-identifier"
              specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (conf-spec/evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is nil")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"
                                    :api-group    nil}]
          (is (= configuration-source
                (conf-spec/evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is not present")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-group nil})
                (conf-spec/evaluate specification configuration-source)))))

      (testing (str "returns converted parameter when type specified and value "
                 "is convertible")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-port :type :integer))
              configuration-source {:api-port "5000"}]
          (is (= {:api-port 5000}
                (conf-spec/evaluate specification configuration-source)))))

      (testing "uses custom converter when defined"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :encrypted? :type :boolean))
              configuration-source {:encrypted? "true"}]
          (is (= {:encrypted? true}
                (conf-spec/evaluate specification configuration-source)))))

      (testing "throws exception when parameter fails to convert"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-identifier)
                              (conf/with-parameter :api-port :type :integer))
              configuration-source {:api-identifier "some-identifier"
                                    :api-port       "abcd"}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [:api-port].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       []
                  :invalid       []
                  :unconvertible [:api-port]
                  :reasons       {}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-identifier])}
                (ex-data exception)))))

      (testing (str "throws exception with all unconvertible parameters when "
                 "multiple parameters fail to convert")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-port1 :type :integer)
                              (conf/with-parameter :api-port2 :type :integer)
                              (conf/with-parameter :api-group))
              configuration-source {:api-port1 "abcd"
                                    :api-port2 "efgh"
                                    :api-group "some-group"}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [:api-port1 :api-port2].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       []
                  :invalid       []
                  :unconvertible [:api-port1
                                  :api-port2]
                  :original      configuration-source
                  :reasons       {}
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all unconvertible and missing "
                 "parameters when multiple parameters are in error")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-port1 :type :integer)
                              (conf/with-parameter :api-port2 :type :integer)
                              (conf/with-parameter :api-group :nilable true))
              configuration-source {:api-username nil
                                    :api-password nil
                                    :api-port1    "abcd"
                                    :api-port2    "efgh"
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [:api-username :api-password], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [:api-port1 :api-port2].")
                (.getMessage ^Exception exception)))
          (is (= {:missing       [:api-username
                                  :api-password]
                  :invalid       []
                  :unconvertible [:api-port1
                                  :api-port2]
                  :reasons       {}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing "converts default"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-port
                                :type :integer
                                :default "5000"))
              configuration-source {:api-port nil}]
          (is (= {:api-port 5000}
                (conf-spec/evaluate specification configuration-source)))))

      (testing "nilable parameters convert correctly when nil"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-port
                                :type :integer
                                :nilable true)
                              (conf/with-parameter :api-host
                                :type :string
                                :nilable true))
              configuration-source {:api-port nil
                                    :api-host nil}]
          (is (= {:api-port nil
                  :api-host nil}
                (conf-spec/evaluate specification configuration-source)))))

      (testing "applies key function to each key before returning"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-key-fn (conf-kf/remove-prefix :api)))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:username "some-username"
                  :password "some-password"}
                (conf-spec/evaluate specification configuration-source)))))

      (testing (str "applies many key functions to each key in supplied order "
                 "before returning")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-key-fn (conf-kf/remove-prefix :api))
                              (conf/with-key-fn (conf-kf/add-prefix :service)))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:service-username "some-username"
                  :service-password "some-password"}
                (conf-spec/evaluate specification configuration-source)))))

      (testing "applies transformation to configuration map before returning"
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-transformation
                                (fn [m] {:credentials m})))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:credentials
                  {:api-username "some-username"
                   :api-password "some-password"}}
                (conf-spec/evaluate specification configuration-source)))))

      (testing (str "applies many transformations to configuration map "
                 "before returning")
        (let [specification (conf/configuration-specification
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-transformation
                                (fn [m] {:credentials m}))
                              (conf/with-transformation
                                (fn [m] (merge {:timeout 5000} m))))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:credentials {:api-username "some-username"
                                :api-password "some-password"}
                  :timeout     5000}
                (conf-spec/evaluate specification configuration-source))))))))

(deftest configuration-sources
  (testing "map source"
    (is (= 1 (:first (conf/map-source {:first 1 :second 2}))))
    (is (= nil (:third (conf/map-source {:first 1 :second 2})))))

  (testing "env source"
    (is (= (System/getenv "USER")
          (:user (conf/env-source)))))

  (testing "environ source"
    (with-redefs [environ.core/env {:some-service-username "some-username"}]
      (is (= "some-username"
            (:username (conf/environ-source :prefix :some-service))))
      (is (= nil
            (:password (conf/environ-source :prefix :some-service))))))

  (testing "YAML file source"
    (with-redefs
     [clojure.core/slurp (fn [path]
                           (if (= path "path/to/config.yaml")
                             (str
                               "---\n"
                               "database_username: \"some-username\"\n"
                               "database_password: \"some-password\"\n")))]
      (is (= "some-username"
            (:database-username (conf/yaml-file-source "path/to/config.yaml"))))
      (is (= "some-username"
            (:username (conf/yaml-file-source "path/to/config.yaml"
                         :prefix :database))))
      (is (= nil
            (:host (conf/yaml-file-source "path/to/config.yaml"
                     :prefix :database))))))

  (testing "multi source"
    (with-redefs
     [clojure.core/slurp (fn [path]
                           (if (= path "path/to/config.yaml")
                             (str
                               "---\n"
                               "api_username: \"some-username\"\n"
                               "api_password: \"some-password\"\n")))]
      (let [source (conf/multi-source
                     (conf/yaml-file-source "path/to/config.yaml")
                     (conf/map-source {:api-username "default-username"
                                       :api-port     "5000"}))]
        (is (= "some-username" (:api-username source)))
        (is (= "5000" (:api-port source)))
        (is (= nil (:api-host source)))))))

(deftest configuration-definition
  (testing "construction"
    (testing "allows adding predefined parameter"
      (let [parameter
            (conf/parameter :api-username {:default "admin"})]
        (is (= (conf/configuration
                 (conf/with-parameter parameter))
              (conf/configuration
                (conf/with-parameter :api-username :default "admin"))))))

    (testing "allows copying from existing configuration"
      (let [key-fn (conf-kf/remove-prefix :api)
            transformation
            (fn [config]
              (assoc config :description "Some API"))
            source
            (conf/map-source
              {:api-base-url "https://example.com"
               :api-username "admin"
               :api-password "super-secret"})
            specification-1
            (conf/configuration-specification
              (conf/with-parameter :api-base-url))
            specification-2
            (conf/configuration-specification
              (conf/with-parameter :api-username))
            existing-configuration
            (conf/configuration
              (conf/with-specification specification-1)
              (conf/with-specification specification-2)
              (conf/with-source source))
            new-configuration
            (conf/configuration
              (conf/from-configuration existing-configuration)
              (conf/with-key-fn key-fn)
              (conf/with-transformation transformation)
              (conf/with-parameter :api-password))]
        (is (= {:base-url    "https://example.com"
                :username    "admin"
                :password    "super-secret"
                :description "Some API"}
              (conf/resolve new-configuration))))))

  (testing "resolve"
    (testing "resolves all parameters in the specification"
      (let [configuration
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:api-username "some-username"
                   :api-port     "5000"}))
              (conf/with-parameter :api-username)
              (conf/with-parameter :api-port
                :type :integer))]
        (is (= {:api-username "some-username"
                :api-port     5000}
              (conf/resolve configuration)))))

    (testing "resolves from multiple sources"
      (with-redefs
       [clojure.core/slurp (fn [path]
                             (if (= path "path/to/config.yaml")
                               (str
                                 "---\n"
                                 "api_username: \"some-username\"\n"
                                 "api_password: \"some-password\"\n")))]
        (let [configuration (conf/configuration
                              (conf/with-source
                                (conf/map-source {:api-port "5000"}))
                              (conf/with-source
                                (conf/yaml-file-source "path/to/config.yaml"))
                              (conf/with-parameter :api-username)
                              (conf/with-parameter :api-password)
                              (conf/with-parameter :api-port
                                :type :integer))]
          (is (= {:api-username "some-username"
                  :api-password "some-password"
                  :api-port     5000}
                (conf/resolve configuration))))))

    (testing "can be created from an existing specification"
      (let [specification (conf/configuration-specification
                            (conf/with-parameter :api-username)
                            (conf/with-parameter :api-password))
            configuration (conf/configuration
                            (conf/with-source
                              (conf/map-source {:api-username "some-username"
                                                :api-password "some-password"
                                                :api-port     "5000"}))
                            (conf/with-specification specification)
                            (conf/with-parameter :api-port :type :integer))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (conf/resolve configuration)))))

    (testing "can be created from multiple existing specifications"
      (let [specification1 (conf/configuration-specification
                             (conf/with-parameter :api-username)
                             (conf/with-parameter :api-password))
            specification2 (conf/configuration-specification
                             (conf/with-parameter :api-port :type :integer))
            configuration (conf/configuration
                            (conf/with-source
                              (conf/map-source {:api-username "some-username"
                                                :api-password "some-password"
                                                :api-port     "5000"}))
                            (conf/with-specification specification1)
                            (conf/with-specification specification2))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (conf/resolve configuration)))))

    (testing "scopes key functions when defined on specifications"
      (let [specification1
            (conf/configuration-specification
              (conf/with-parameter :db-username)
              (conf/with-key-fn (conf-kf/remove-prefix :db))
              (conf/with-key-fn (conf-kf/add-prefix :postgres)))
            specification2
            (conf/configuration-specification
              (conf/with-parameter :database-password)
              (conf/with-key-fn
                (conf-kf/remove-prefix :database))
              (conf/with-key-fn (conf-kf/add-prefix :postgres)))
            configuration
            (conf/configuration
              (conf/with-source {:db-username       "some-username"
                                 :database-password "some-password"})
              (conf/with-specification specification1)
              (conf/with-specification specification2))]
        (is (= {:postgres-username "some-username"
                :postgres-password "some-password"}
              (conf/resolve configuration)))))

    (testing "uses provided key functions"
      (let [configuration
            (conf/configuration
              (conf/with-source
                (conf/map-source {:api-username "some-username"
                                  :api-password "some-password"
                                  :api-port     "5000"}))
              (conf/with-parameter :api-username)
              (conf/with-parameter :api-password)
              (conf/with-parameter :api-port :type :integer)
              (conf/with-key-fn (conf-kf/remove-prefix :api))
              (conf/with-key-fn (conf-kf/add-prefix :service)))]
        (is (= {:service-username "some-username"
                :service-password "some-password"
                :service-port     5000}
              (conf/resolve configuration)))))

    (testing "scopes transformations when defined on specifications"
      (let [specification1 (conf/configuration-specification
                             (conf/with-parameter :username)
                             (conf/with-parameter :password)
                             (conf/with-transformation
                               (fn [m] {:credentials m})))
            specification2 (conf/configuration-specification
                             (conf/with-parameter :timeout :type :integer)
                             (conf/with-transformation
                               (fn [m] {:options m})))
            configuration (conf/configuration
                            (conf/with-source {:username "some-username"
                                               :password "some-password"
                                               :timeout  10000})
                            (conf/with-specification specification1)
                            (conf/with-specification specification2))]
        (is (= {:credentials {:username "some-username"
                              :password "some-password"}
                :options     {:timeout 10000}}
              (conf/resolve configuration)))))

    (testing "uses provided transformations"
      (let [configuration (conf/configuration
                            (conf/with-source
                              (conf/map-source {:username "some-username"
                                                :password "some-password"}))
                            (conf/with-parameter :username)
                            (conf/with-parameter :password)
                            (conf/with-transformation
                              (fn [m] {:credentials m}))
                            (conf/with-transformation
                              (fn [m] (merge {:options {:timeout 5000}} m))))]
        (is (= {:credentials {:username "some-username"
                              :password "some-password"}
                :options     {:timeout 5000}}
              (conf/resolve configuration)))))

    (testing "applies source middleware before resolution"
      (let [password-masking-middleware
            (fn [source parameter-name]
              (let [parameter-value (get source parameter-name)]
                (if (= parameter-name :api-credentials)
                  (assoc parameter-value
                    :pass (apply str
                            (take (count (:pass parameter-value))
                              (repeat "*"))))
                  parameter-value)))

            configuration
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:api-credentials
                   "{\"user\": \"james\",\"pass\": \"X4ftRd32\"}"
                   :api-timeout
                   "10000"})
                (conf/with-json-parsing)
                (conf/with-middleware password-masking-middleware))
              (conf/with-parameter :api-credentials :type :map)
              (conf/with-parameter :api-timeout :type :integer))]
        (is (= {:api-timeout     10000
                :api-credentials {:user "james"
                                  :pass "********"}}
              (conf/resolve configuration))))))

  (testing "merge"
    (testing "resolves parameters across all definitions"
      (let [configuration-1
            (conf/configuration
              (conf/with-source
                (conf/map-source {:api-username "api-username"
                                  :api-password "api-password"}))
              (conf/with-parameter :api-username)
              (conf/with-parameter :api-password))
            configuration-2
            (conf/configuration
              (conf/with-source
                (conf/map-source {:db-username "db-username"
                                  :db-password "db-password"}))
              (conf/with-parameter :db-username)
              (conf/with-parameter :db-password))
            merged (conf/merge configuration-1 configuration-2)]
        (is (= {:api-username "api-username"
                :api-password "api-password"
                :db-username  "db-username"
                :db-password  "db-password"}
              (conf/resolve merged)))))

    (testing "uses only sources defined within each configuration definition"
      (let [configuration-1
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:api-username "api-username"
                   :api-password "api-password"
                   :db-username  "other-db-username"
                   :db-password  "other-db-password"}))
              (conf/with-parameter :api-username)
              (conf/with-parameter :api-password))
            configuration-2
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:api-username "other-api-username"
                   :api-password "other-api-password"
                   :db-username  "db-username"
                   :db-password  "db-password"}))
              (conf/with-parameter :db-username)
              (conf/with-parameter :db-password))
            merged (conf/merge configuration-1 configuration-2)]
        (is (= {:api-username "api-username"
                :api-password "api-password"
                :db-username  "db-username"
                :db-password  "db-password"}
              (conf/resolve merged)))))

    (testing "scopes key functions to each configuration definition"
      (let [configuration-1
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:api-username "api-username"
                   :api-password "api-password"}))
              (conf/with-parameter :api-username)
              (conf/with-parameter :api-password)
              (conf/with-key-fn (conf-kf/remove-prefix :api))
              (conf/with-key-fn (conf-kf/add-prefix :service)))
            configuration-2
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:db-username "db-username"
                   :db-password "db-password"}))
              (conf/with-parameter :db-username)
              (conf/with-parameter :db-password)
              (conf/with-key-fn (conf-kf/remove-prefix :db))
              (conf/with-key-fn (conf-kf/add-prefix :database)))
            merged (conf/merge configuration-1 configuration-2)]
        (is (= {:service-username  "api-username"
                :service-password  "api-password"
                :database-username "db-username"
                :database-password "db-password"}
              (conf/resolve merged)))))

    (testing "scopes transformations to each configuration definition"
      (let [configuration-1
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:username "api-username"
                   :password "api-password"}))
              (conf/with-parameter :username)
              (conf/with-parameter :password)
              (conf/with-transformation
                (fn [m] (merge {:timeout 10000} m)))
              (conf/with-transformation
                (fn [m] {:api m})))
            configuration-2
            (conf/configuration
              (conf/with-source
                (conf/map-source
                  {:username "db-username"
                   :password "db-password"}))
              (conf/with-parameter :username)
              (conf/with-parameter :password)
              (conf/with-transformation
                (fn [m] (merge {:timeout 30000} m)))
              (conf/with-transformation
                (fn [m] {:db m})))
            merged (conf/merge configuration-1 configuration-2)]
        (is (= {:api {:username "api-username"
                      :password "api-password"
                      :timeout  10000}
                :db  {:username "db-username"
                      :password "db-password"
                      :timeout  30000}}
              (conf/resolve merged)))))))
