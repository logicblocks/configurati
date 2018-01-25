(ns configurati.core-test
  (:require
    [clojure.test :refer :all]

    [configurati.core :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(defn evaluate-and-catch [specification configuration-map]
  (try
    (evaluate specification configuration-map)
    (catch ExceptionInfo e e)))

(deftest configuration-parameters
  (testing "construction"
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable false :default nil :as :string})
          (with-parameter :api-username)))
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable false :default nil :as :string})
          (with-parameter :api-username :nilable false)))
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable true :default nil :as :string})
          (with-parameter :api-username :nilable true)))
    (is (= (map->ConfigurationParameter
             {:name    :api-username
              :nilable false
              :default "username"
              :as      :string})
          (with-parameter :api-username :default "username")))
    (is (= (map->ConfigurationParameter
             {:name    :api-port
              :nilable false
              :default nil
              :as      :integer})
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
    (testing "returns configuration map when no errors occur"
      (let [specification (configuration-specification
                            (with-parameter :api-username :nilable false)
                            (with-parameter :api-password :nilable false))
            configuration-map {:api-username "some-username"
                               :api-password "some-password"}]
        (is (= configuration-map
              (evaluate specification configuration-map)))))

    (testing "throws exception when non-nilable parameter is nil"
      (let [specification (configuration-specification
                            (with-parameter :api-username :nilable false)
                            (with-parameter :api-password :nilable false)
                            (with-parameter :api-group :nilable true))
            configuration-map {:api-username nil
                               :api-password "some-password"
                               :api-group    nil}
            exception (evaluate-and-catch
                        specification configuration-map)]
        (is (= ExceptionInfo (type exception)))
        (is (= (str "Configuration evaluation failed. "
                 "Missing parameters: [:api-username], "
                 "unconvertible parameters: [].")
              (.getMessage exception)))
        (is (= {:missing       [:api-username]
                :unconvertible []
                :original      configuration-map
                :evaluated     (select-keys configuration-map
                                 [:api-password :api-group])}
              (ex-data exception)))))

    (testing (str "throws exception with all missing parameters when "
               "multiple non-nilable parameters are nil")
      (let [specification (configuration-specification
                            (with-parameter :api-username :nilable false)
                            (with-parameter :api-password :nilable false)
                            (with-parameter :api-group :nilable true))
            configuration-map {:api-username nil
                               :api-password nil
                               :api-group    nil}
            exception (evaluate-and-catch
                        specification configuration-map)]
        (is (= ExceptionInfo (type exception)))
        (is (= (str "Configuration evaluation failed. "
                 "Missing parameters: [:api-username :api-password], "
                 "unconvertible parameters: [].")
              (.getMessage exception)))
        (is (= {:missing       [:api-username
                                :api-password]
                :unconvertible []
                :original      configuration-map
                :evaluated     (select-keys configuration-map
                                 [:api-group])}
              (ex-data exception)))))

    (testing "returns provided default when parameter is nil"
      (let [default-identifier "default-identifier"
            specification (configuration-specification
                            (with-parameter :api-username)
                            (with-parameter :api-password)
                            (with-parameter :api-identifier
                              :default default-identifier))
            configuration-map {:api-username   "some-username"
                               :api-password   "some-password"
                               :api-identifier nil}]
        (is (= (merge configuration-map
                 {:api-identifier default-identifier})
              (evaluate specification configuration-map)))))

    (testing "returns provided default when parameter is not present"
      (let [default-identifier "default-identifier"
            specification (configuration-specification
                            (with-parameter :api-username)
                            (with-parameter :api-password)
                            (with-parameter :api-identifier
                              :default default-identifier))
            configuration-map {:api-username "some-username"
                               :api-password "some-password"}]
        (is (= (merge configuration-map
                 {:api-identifier default-identifier})
              (evaluate specification configuration-map)))))

    (testing (str "returns nil when nilable, no default specified and "
               "parameter is nil")
      (let [specification (configuration-specification
                            (with-parameter :api-username)
                            (with-parameter :api-password)
                            (with-parameter :api-group :nilable true))
            configuration-map {:api-username "some-username"
                               :api-password "some-password"
                               :api-group    nil}]
        (is (= configuration-map
              (evaluate specification configuration-map)))))

    (testing (str "returns nil when nilable, no default specified and "
               "parameter is not present")
      (let [specification (configuration-specification
                            (with-parameter :api-username)
                            (with-parameter :api-password)
                            (with-parameter :api-group :nilable true))
            configuration-map {:api-username "some-username"
                               :api-password "some-password"}]
        (is (= (merge configuration-map
                 {:api-group nil})
              (evaluate specification configuration-map)))))

    (testing (str "returns converted parameter when type specified and value "
               "is convertible")
      (let [specification (configuration-specification
                            (with-parameter :api-port :as :integer))
            configuration-map {:api-port "5000"}]
        (is (= {:api-port 5000}
              (evaluate specification configuration-map)))))

    (testing "throws exception when parameter fails to convert"
      (let [specification (configuration-specification
                            (with-parameter :api-identifier)
                            (with-parameter :api-port :as :integer))
            configuration-map {:api-identifier "some-identifier"
                               :api-port       "abcd"}
            exception (evaluate-and-catch
                        specification configuration-map)]
        (is (= ExceptionInfo (type exception)))
        (is (= (str "Configuration evaluation failed. "
                 "Missing parameters: [], "
                 "unconvertible parameters: [:api-port].")
              (.getMessage exception)))
        (is (= {:missing       []
                :unconvertible [:api-port]
                :original      configuration-map
                :evaluated     (select-keys configuration-map
                                 [:api-identifier])}
              (ex-data exception)))))

    (testing (str "throws exception with all unconvertible parameters when "
               "multiple parameters fail to convert")
      (let [specification (configuration-specification
                            (with-parameter :api-port1 :as :integer)
                            (with-parameter :api-port2 :as :integer)
                            (with-parameter :api-group))
            configuration-map {:api-port1 "abcd"
                               :api-port2 "efgh"
                               :api-group "some-group"}
            exception (evaluate-and-catch
                        specification configuration-map)]
        (is (= ExceptionInfo (type exception)))
        (is (= (str "Configuration evaluation failed. "
                 "Missing parameters: [], "
                 "unconvertible parameters: [:api-port1 :api-port2].")
              (.getMessage exception)))
        (is (= {:missing       []
                :unconvertible [:api-port1
                                :api-port2]
                :original      configuration-map
                :evaluated     (select-keys configuration-map
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
            configuration-map {:api-username nil
                               :api-password nil
                               :api-port1 "abcd"
                               :api-port2 "efgh"
                               :api-group nil}
            exception (evaluate-and-catch
                        specification configuration-map)]
        (is (= ExceptionInfo (type exception)))
        (is (= (str "Configuration evaluation failed. "
                 "Missing parameters: [:api-username :api-password], "
                 "unconvertible parameters: [:api-port1 :api-port2].")
              (.getMessage exception)))
        (is (= {:missing       [:api-username
                                :api-password]
                :unconvertible [:api-port1
                                :api-port2]
                :original      configuration-map
                :evaluated     (select-keys configuration-map
                                 [:api-group])}
              (ex-data exception)))))

    (testing "converts default"
      (let [specification (configuration-specification
                            (with-parameter :api-port
                              :as :integer
                              :default "5000"))
            configuration-map {:api-port nil}]
        (is (= {:api-port 5000}
              (evaluate specification configuration-map)))))

    (testing "nilables parameters convert correctly when nil"
      (let [specification (configuration-specification
                            (with-parameter :api-port
                              :as :integer
                              :nilable true)
                            (with-parameter :api-host
                              :as :string
                              :nilable true))
            configuration-map {:api-port nil
                               :api-host nil}]
        (is (= {:api-port nil
                :api-host nil}
              (evaluate specification configuration-map)))))))
