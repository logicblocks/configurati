(ns configurati.core-test
  (:refer-clojure :exclude [replace resolve])
  (:require
    [clojure.test :refer :all]
    [clojure.string :refer [replace]]

    [configurati.core :refer :all]
    [configurati.parameters
     :refer [map->ConfigurationParameter
             default
             validate
             convert]]
    [configurati.specification
     :refer [evaluate]]
    [configurati.conversions :refer [convert-to]])
  (:import [clojure.lang ExceptionInfo]))

(defmethod convert-to :boolean [_ value]
  (if (#{"true" true} value) true false))

(deftest configuration-parameters
  (testing "construction"
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default nil
                          :as      :string})]
          (with-parameter :api-username)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default nil
                          :as      :string})]
          (with-parameter :api-username :nilable false)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable true
                          :default nil
                          :as      :string})]
          (with-parameter :api-username :nilable true)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default "username"
                          :as      :string})]
          (with-parameter :api-username :default "username")))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-port
                          :nilable false
                          :default nil
                          :as      :integer})]
          (with-parameter :api-port :as :integer))))

  (testing "defaulting"
    (let [parameter (map->ConfigurationParameter
                      {:name    :api-password
                       :nilable false
                       :default "P@55w0rd"})]
      (is (= "P@55w0rd" (default parameter nil))))
    (let [parameter (map->ConfigurationParameter
                      {:name    :api-password
                       :nilable false
                       :default nil})]
      (is (= nil (default parameter nil)))))

  (testing "validation"
    (let [parameter (map->ConfigurationParameter
                      {:name :api-username :nilable false :default nil})]
      (is (= {:error :missing :value nil}
            (validate parameter nil)))
      (is (= {:error nil :value "username"}
            (validate parameter "username")))))

  (testing "conversion"
    (let [parameter (map->ConfigurationParameter
                      {:name    :api-username
                       :nilable false
                       :default nil
                       :as      :integer})]
      (is (= {:error nil :value 5000}
            (convert parameter "5000")))
      (is (= {:error :unconvertible :value nil}
            (convert parameter "abcd"))))))

(deftest configuration-specifications
  (testing "evaluate"
    (let [evaluate-and-catch (fn [specification configuration-source]
                               (try
                                 (evaluate specification configuration-source)
                                 (catch ExceptionInfo e e)))]
      (testing "returns configuration map when no errors occur"
        (let [specification (configuration-specification
                              (with-parameter :api-username :nilable false)
                              (with-parameter :api-password :nilable false))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= configuration-source
                (evaluate specification configuration-source)))))

      (testing "throws exception when non-nilable parameter is nil"
        (let [specification (configuration-specification
                              (with-parameter :api-username :nilable false)
                              (with-parameter :api-password :nilable false)
                              (with-parameter :api-group :nilable true))
              configuration-source {:api-username nil
                                    :api-password "some-password"
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [:api-username], "
                   "unconvertible parameters: [].")
                (.getMessage exception)))
          (is (= {:missing       [:api-username]
                  :unconvertible []
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-password :api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all missing parameters when "
                 "multiple non-nilable parameters are nil")
        (let [specification (configuration-specification
                              (with-parameter :api-username :nilable false)
                              (with-parameter :api-password :nilable false)
                              (with-parameter :api-group :nilable true))
              configuration-source {:api-username nil
                                    :api-password nil
                                    :api-group    nil}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [:api-username :api-password], "
                   "unconvertible parameters: [].")
                (.getMessage exception)))
          (is (= {:missing       [:api-username
                                  :api-password]
                  :unconvertible []
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing "returns provided default when parameter is nil"
        (let [default-identifier "default-identifier"
              specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username   "some-username"
                                    :api-password   "some-password"
                                    :api-identifier nil}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (evaluate specification configuration-source)))))

      (testing "returns provided default when parameter is not present"
        (let [default-identifier "default-identifier"
              specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is nil")
        (let [specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"
                                    :api-group    nil}]
          (is (= configuration-source
                (evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is not present")
        (let [specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-group nil})
                (evaluate specification configuration-source)))))

      (testing (str "returns converted parameter when type specified and value "
                 "is convertible")
        (let [specification (configuration-specification
                              (with-parameter :api-port :as :integer))
              configuration-source {:api-port "5000"}]
          (is (= {:api-port 5000}
                (evaluate specification configuration-source)))))

      (testing "uses custom converter when defined"
        (let [specification (configuration-specification
                              (with-parameter :encrypted? :as :boolean))
              configuration-source {:encrypted? "true"}]
          (is (= {:encrypted? true}
                (evaluate specification configuration-source)))))

      (testing "throws exception when parameter fails to convert"
        (let [specification (configuration-specification
                              (with-parameter :api-identifier)
                              (with-parameter :api-port :as :integer))
              configuration-source {:api-identifier "some-identifier"
                                    :api-port       "abcd"}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "unconvertible parameters: [:api-port].")
                (.getMessage exception)))
          (is (= {:missing       []
                  :unconvertible [:api-port]
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-identifier])}
                (ex-data exception)))))

      (testing (str "throws exception with all unconvertible parameters when "
                 "multiple parameters fail to convert")
        (let [specification (configuration-specification
                              (with-parameter :api-port1 :as :integer)
                              (with-parameter :api-port2 :as :integer)
                              (with-parameter :api-group))
              configuration-source {:api-port1 "abcd"
                                    :api-port2 "efgh"
                                    :api-group "some-group"}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "unconvertible parameters: [:api-port1 :api-port2].")
                (.getMessage exception)))
          (is (= {:missing       []
                  :unconvertible [:api-port1
                                  :api-port2]
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all unconvertible and missing "
                 "parameters when multiple parameters are in error")
        (let [specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-port1 :as :integer)
                              (with-parameter :api-port2 :as :integer)
                              (with-parameter :api-group :nilable true))
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
                   "unconvertible parameters: [:api-port1 :api-port2].")
                (.getMessage exception)))
          (is (= {:missing       [:api-username
                                  :api-password]
                  :unconvertible [:api-port1
                                  :api-port2]
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing "converts default"
        (let [specification (configuration-specification
                              (with-parameter :api-port
                                :as :integer
                                :default "5000"))
              configuration-source {:api-port nil}]
          (is (= {:api-port 5000}
                (evaluate specification configuration-source)))))

      (testing "nilable parameters convert correctly when nil"
        (let [specification (configuration-specification
                              (with-parameter :api-port
                                :as :integer
                                :nilable true)
                              (with-parameter :api-host
                                :as :string
                                :nilable true))
              configuration-source {:api-port nil
                                    :api-host nil}]
          (is (= {:api-port nil
                  :api-host nil}
                (evaluate specification configuration-source)))))

      (testing "applies key function to each key before returning"
        (let [specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-key-fn
                                #(keyword (replace (name %) "api-" ""))))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:username "some-username"
                  :password "some-password"}
                (evaluate specification configuration-source)))))

      (testing (str "applies many key functions to each key in supplied order "
                 "before returning")
        (let [specification (configuration-specification
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-key-fn
                                #(keyword (replace (name %) "api-" "")))
                              (with-key-fn
                                #(keyword (str "service-" (name %)))))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:service-username "some-username"
                  :service-password "some-password"}
                (evaluate specification configuration-source))))))))

(deftest configuration-sources
  (testing "map source"
    (is (= 1 (:first (map-source {:first 1 :second 2}))))
    (is (= nil (:third (map-source {:first 1 :second 2})))))

  (testing "env source"
    (with-redefs [environ.core/env {:some-service-username "some-username"}]
      (is (= "some-username"
            (:username (env-source :prefix :some-service))))
      (is (= nil
            (:password (env-source :prefix :some-service))))))

  (testing "YAML file source"
    (with-redefs
      [clojure.core/slurp (fn [path]
                            (if (= path "path/to/config.yaml")
                              (str
                                "---\n"
                                "database_username: \"some-username\"\n"
                                "database_password: \"some-password\"\n")))]
      (is (= "some-username"
            (:database-username (yaml-file-source "path/to/config.yaml"))))
      (is (= "some-username"
            (:username (yaml-file-source "path/to/config.yaml"
                         :prefix :database))))
      (is (= nil
            (:host (yaml-file-source "path/to/config.yaml"
                     :prefix :database))))))

  (testing "multi source"
    (with-redefs
      [clojure.core/slurp (fn [path]
                            (if (= path "path/to/config.yaml")
                              (str
                                "---\n"
                                "api_username: \"some-username\"\n"
                                "api_password: \"some-password\"\n")))]
      (let [source (multi-source
                     (yaml-file-source "path/to/config.yaml")
                     (map-source {:api-username "default-username"
                                  :api-port     "5000"}))]
        (is (= "some-username" (:api-username source)))
        (is (= "5000" (:api-port source)))
        (is (= nil (:api-host source)))))))

(deftest configuration-definition
  (testing "resolve"
    (testing "resolves all parameters in the specification"
      (let [configuration (define-configuration
                            (with-source (map-source
                                           {:api-username "some-username"
                                            :api-port     "5000"}))
                            (with-parameter :api-username)
                            (with-parameter :api-port
                              :as :integer))]
        (is (= {:api-username "some-username"
                :api-port     5000}
              (resolve configuration)))))

    (testing "resolves from multiple sources"
      (with-redefs
        [clojure.core/slurp (fn [path]
                              (if (= path "path/to/config.yaml")
                                (str
                                  "---\n"
                                  "api_username: \"some-username\"\n"
                                  "api_password: \"some-password\"\n")))]
        (let [configuration (define-configuration
                              (with-source
                                (map-source {:api-port "5000"}))
                              (with-source
                                (yaml-file-source "path/to/config.yaml"))
                              (with-parameter :api-username)
                              (with-parameter :api-password)
                              (with-parameter :api-port
                                :as :integer))]
          (is (= {:api-username "some-username"
                  :api-password "some-password"
                  :api-port     5000}
                (resolve configuration))))))

    (testing "can be created from an existing specification"
      (let [specification (configuration-specification
                            (with-parameter :api-username)
                            (with-parameter :api-password))
            configuration (define-configuration
                            (with-source
                              (map-source {:api-username "some-username"
                                           :api-password "some-password"
                                           :api-port     "5000"}))
                            (with-specification specification)
                            (with-parameter :api-port :as :integer))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (resolve configuration)))))

    (testing "can be created from multiple existing specifications"
      (let [specification1 (configuration-specification
                             (with-parameter :api-username)
                             (with-parameter :api-password))
            specification2 (configuration-specification
                             (with-parameter :api-port :as :integer))
            configuration (define-configuration
                            (with-source
                              (map-source {:api-username "some-username"
                                           :api-password "some-password"
                                           :api-port     "5000"}))
                            (with-specification specification1)
                            (with-specification specification2))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (resolve configuration)))))

    (testing "uses provided key functions"
      (let [configuration (define-configuration
                            (with-source
                              (map-source {:api-username "some-username"
                                           :api-password "some-password"
                                           :api-port     "5000"}))
                            (with-parameter :api-username)
                            (with-parameter :api-password)
                            (with-parameter :api-port :as :integer)
                            (with-key-fn
                              #(keyword (replace (name %) "api-" "")))
                            (with-key-fn
                              #(keyword (str "service-" (name %)))))]
        (is (= {:service-username "some-username"
                :service-password "some-password"
                :service-port     5000}
              (resolve configuration)))))))
