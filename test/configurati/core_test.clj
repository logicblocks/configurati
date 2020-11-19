(ns configurati.core-test
  (:refer-clojure :exclude [replace resolve])
  (:require
   [clojure.test :refer :all]
   [clojure.string :refer [replace]]
   [clojure.spec.alpha :as spec]

   [cheshire.core :as json]

   [configurati.core :as c]
   [configurati.key-fns
    :refer [add-prefix
            remove-prefix]]
   [configurati.parameters
    :refer [map->ConfigurationParameter
            default
            validate
            convert]]
   [configurati.specification
    :refer [evaluate]]
   [configurati.conversions :refer [convert-to]]
   [configurati.middleware
    :refer [json-parsing-middleware
            separator-parsing-middleware]]
   [clojure.string :as string])
  (:import [clojure.lang ExceptionInfo]))

(defmethod convert-to :boolean [_ value]
  (if (#{"true" true} value) true false))

(deftest configuration-parameters
  (testing "construction"
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default nil
                          :type    :string})]
          (c/with-parameter :api-username)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default nil
                          :type    :string})]
          (c/with-parameter :api-username :nilable false)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable true
                          :default nil
                          :type    :string})]
          (c/with-parameter :api-username :nilable true)))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-username
                          :nilable false
                          :default "username"
                          :type    :string})]
          (c/with-parameter :api-username :default "username")))
    (is (= [:parameter (map->ConfigurationParameter
                         {:name    :api-port
                          :nilable false
                          :default nil
                          :type    :integer})]
          (c/with-parameter :api-port :type :integer))))

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
    (testing "for nilable"
      (let [parameter (map->ConfigurationParameter
                        {:name :api-username :nilable false :default nil})]
        (is (= {:error :missing :value nil}
              (validate parameter nil)))
        (is (= {:error nil :value "username"}
              (validate parameter "username")))))

    (testing "for spec"
      (let [spec (fn [value] (>= (count value) 8))
            parameter (map->ConfigurationParameter
                        {:name :api-username :spec spec})]
        (is (= {:error  :invalid
                :value  "user"
                :reason (spec/explain-data spec "user")}
              (validate parameter "user")))
        (is (= {:error nil
                :value "username"}
              (validate parameter "username"))))))

  (testing "conversion"
    (let [parameter (map->ConfigurationParameter
                      {:name    :api-username
                       :nilable false
                       :default nil
                       :type    :integer})]
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
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username :nilable false)
                              (c/with-parameter :api-password :nilable false))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= configuration-source
                (evaluate specification configuration-source)))))

      (testing "throws exception when non-nilable parameter is nil"
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username :nilable false)
                              (c/with-parameter :api-password :nilable false)
                              (c/with-parameter :api-group :nilable true))
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
                (.getMessage exception)))
          (is (= {:missing       [:api-username]
                  :invalid       []
                  :unconvertible []
                  :reasons       {}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-password :api-group])}
                (ex-data exception)))))

      (testing "throws exception when parameter does not match spec"
        (let [spec (fn [value] (>= (count value) 8))
              specification
              (c/define-configuration-specification
                (c/with-parameter :api-username :spec spec)
                (c/with-parameter :api-password :nilable false)
                (c/with-parameter :api-group :nilable true))
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
                (.getMessage exception)))
          (is (= {:missing       []
                  :invalid       [:api-username]
                  :unconvertible []
                  :reasons       {:api-username
                                  (spec/explain-data spec "user")}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-password :api-group])}
                (ex-data exception)))))

      (testing (str "throws exception with all missing parameters when "
                 "multiple non-nilable parameters are nil")
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username :nilable false)
                              (c/with-parameter :api-password :nilable false)
                              (c/with-parameter :api-group :nilable true))
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
                (.getMessage exception)))
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
        (let [spec (fn [value] (>= (count value) 8))
              specification
              (c/define-configuration-specification
                (c/with-parameter :api-username :spec spec)
                (c/with-parameter :api-password :spec spec)
                (c/with-parameter :api-group :nilable true))
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
                (.getMessage exception)))
          (is (= {:missing       []
                  :invalid       [:api-username
                                  :api-password]
                  :unconvertible []
                  :reasons       {:api-username
                                  (spec/explain-data spec "user")
                                  :api-password
                                  (spec/explain-data spec "pass")}
                  :original      configuration-source
                  :evaluated     (select-keys configuration-source
                                   [:api-group])}
                (ex-data exception)))))

      (testing "returns provided default when parameter is nil"
        (let [default-identifier "default-identifier"
              specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username   "some-username"
                                    :api-password   "some-password"
                                    :api-identifier nil}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (evaluate specification configuration-source)))))

      (testing "returns provided default when parameter is not present"
        (let [default-identifier "default-identifier"
              specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-identifier
                                :default default-identifier))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-identifier default-identifier})
                (evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is nil")
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"
                                    :api-group    nil}]
          (is (= configuration-source
                (evaluate specification configuration-source)))))

      (testing (str "returns nil when nilable, no default specified and "
                 "parameter is not present")
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-group :nilable true))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= (merge configuration-source
                   {:api-group nil})
                (evaluate specification configuration-source)))))

      (testing (str "returns converted parameter when type specified and value "
                 "is convertible")
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-port :type :integer))
              configuration-source {:api-port "5000"}]
          (is (= {:api-port 5000}
                (evaluate specification configuration-source)))))

      (testing "uses custom converter when defined"
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :encrypted? :type :boolean))
              configuration-source {:encrypted? "true"}]
          (is (= {:encrypted? true}
                (evaluate specification configuration-source)))))

      (testing "throws exception when parameter fails to convert"
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-identifier)
                              (c/with-parameter :api-port :type :integer))
              configuration-source {:api-identifier "some-identifier"
                                    :api-port       "abcd"}
              exception (evaluate-and-catch
                          specification configuration-source)]
          (is (= ExceptionInfo (type exception)))
          (is (= (str "Configuration evaluation failed. "
                   "Missing parameters: [], "
                   "invalid parameters: [], "
                   "unconvertible parameters: [:api-port].")
                (.getMessage exception)))
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
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-port1 :type :integer)
                              (c/with-parameter :api-port2 :type :integer)
                              (c/with-parameter :api-group))
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
                (.getMessage exception)))
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
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-port1 :type :integer)
                              (c/with-parameter :api-port2 :type :integer)
                              (c/with-parameter :api-group :nilable true))
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
                (.getMessage exception)))
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
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-port
                                :type :integer
                                :default "5000"))
              configuration-source {:api-port nil}]
          (is (= {:api-port 5000}
                (evaluate specification configuration-source)))))

      (testing "nilable parameters convert correctly when nil"
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-port
                                :type :integer
                                :nilable true)
                              (c/with-parameter :api-host
                                :type :string
                                :nilable true))
              configuration-source {:api-port nil
                                    :api-host nil}]
          (is (= {:api-port nil
                  :api-host nil}
                (evaluate specification configuration-source)))))

      (testing "applies key function to each key before returning"
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-key-fn (remove-prefix :api)))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:username "some-username"
                  :password "some-password"}
                (evaluate specification configuration-source)))))

      (testing (str "applies many key functions to each key in supplied order "
                 "before returning")
        (let [specification (c/define-configuration-specification
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-key-fn (remove-prefix :api))
                              (c/with-key-fn (add-prefix :service)))
              configuration-source {:api-username "some-username"
                                    :api-password "some-password"}]
          (is (= {:service-username "some-username"
                  :service-password "some-password"}
                (evaluate specification configuration-source))))))))

(deftest configuration-sources
  (testing "map source"
    (is (= 1 (:first (c/map-source {:first 1 :second 2}))))
    (is (= nil (:third (c/map-source {:first 1 :second 2})))))

  (testing "env source"
    (with-redefs [environ.core/env {:some-service-username "some-username"}]
      (is (= "some-username"
            (:username (c/env-source :prefix :some-service))))
      (is (= nil
            (:password (c/env-source :prefix :some-service))))))

  (testing "YAML file source"
    (with-redefs
     [clojure.core/slurp (fn [path]
                           (if (= path "path/to/config.yaml")
                             (str
                               "---\n"
                               "database_username: \"some-username\"\n"
                               "database_password: \"some-password\"\n")))]
      (is (= "some-username"
            (:database-username (c/yaml-file-source "path/to/config.yaml"))))
      (is (= "some-username"
            (:username (c/yaml-file-source "path/to/config.yaml"
                         :prefix :database))))
      (is (= nil
            (:host (c/yaml-file-source "path/to/config.yaml"
                     :prefix :database))))))

  (testing "multi source"
    (with-redefs
     [clojure.core/slurp (fn [path]
                           (if (= path "path/to/config.yaml")
                             (str
                               "---\n"
                               "api_username: \"some-username\"\n"
                               "api_password: \"some-password\"\n")))]
      (let [source (c/multi-source
                     (c/yaml-file-source "path/to/config.yaml")
                     (c/map-source {:api-username "default-username"
                                    :api-port     "5000"}))]
        (is (= "some-username" (:api-username source)))
        (is (= "5000" (:api-port source)))
        (is (= nil (:api-host source)))))))

(deftest configuration-source-middleware
  (testing "JSON parsing middleware"
    (testing "by default applies to all parameters"
      (let [issuer-1-value {:url      "https://issuer-1.example.com"
                            :audience "https://service-1.example.com"}
            issuer-2-value {:url      "https://issuer-2.example.com"
                            :audience "https://service-2.example.com"}
            source (c/map-source
                     {:issuer-1 (json/generate-string issuer-1-value)
                      :issuer-2 (json/generate-string issuer-2-value)})

            middleware (json-parsing-middleware)

            parameter-1-value (middleware source :issuer-1)
            parameter-2-value (middleware source :issuer-2)]
        (is (= issuer-1-value parameter-1-value))
        (is (= issuer-2-value parameter-2-value))))

    (testing "passes through nil value when parameter not available in source"
      (let [source (c/map-source
                     {:issuer
                      (json/generate-string
                        {:url      "https://issuer-1.example.com"
                         :audience "https://service-1.example.com"})})

            middleware (json-parsing-middleware)

            parameter-value (middleware source :other)]
        (is (nil? parameter-value))))

    (testing "when applied to specific parameters"
      (let [issuer-value {:url      "https://issuer-1.example.com"
                          :audience "https://service-1.example.com"}
            timeout-value 10000

            source (c/map-source
                     {:issuer  (json/generate-string issuer-value)
                      :timeout timeout-value})

            middleware (json-parsing-middleware
                         {:only [:issuer]})

            issuer-parameter-value (middleware source :issuer)
            timeout-parameter-value (middleware source :timeout)]
        (is (= issuer-value issuer-parameter-value))
        (is (= timeout-value timeout-parameter-value))))

    (testing "when using a specific JSON parser"
      (let [json-parser (fn [value] (json/parse-string value false))

            source (c/map-source
                     {:issuer (json/generate-string
                                {:url      "https://issuer-1.example.com"
                                 :audience "https://service-1.example.com"})})

            middleware (json-parsing-middleware
                         {:parse-fn json-parser})

            parameter-value (middleware source :issuer)]
        (is (= {"url"      "https://issuer-1.example.com"
                "audience" "https://service-1.example.com"}
              parameter-value))))

    (testing "when using a specific key function"
      (let [key-fn (fn [key] (keyword (str (name key) "-modified")))

            source (c/map-source
                     {:issuer (json/generate-string
                                {:url      "https://issuer-1.example.com"
                                 :audience "https://service-1.example.com"})})

            middleware (json-parsing-middleware
                         {:key-fn key-fn})

            parameter-value (middleware source :issuer)]
        (is (= {:url-modified      "https://issuer-1.example.com"
                :audience-modified "https://service-1.example.com"}
              parameter-value)))))

  (testing "separator parsing middleware"
    (testing "by default applies to all parameters on comma"
      (let [source (c/map-source
                     {:countries "usa, uk, germany"
                      :roles     "admin,support"})

            middleware (separator-parsing-middleware)

            countries-parameter-value (middleware source :countries)
            roles-parameter-value (middleware source :roles)]
        (is (= ["usa" "uk" "germany"] countries-parameter-value))
        (is (= ["admin" "support"] roles-parameter-value))))

    (testing "passes through nil value when parameter not available in source"
      (let [source (c/map-source
                     {:countries "usa,uk,germany"})

            middleware (separator-parsing-middleware)

            parameter-value (middleware source :other)]
        (is (nil? parameter-value))))

    (testing "when applied to specific parameters"
      (let [source (c/map-source
                     {:client    "Company, UK"
                      :countries "usa,uk,germany"})

            middleware (separator-parsing-middleware
                         {:only [:countries]})

            client-parameter-value (middleware source :client)
            countries-parameter-value (middleware source :countries)]
        (is (= ["usa" "uk" "germany"] countries-parameter-value))
        (is (= "Company, UK" client-parameter-value))))

    (testing "when using a specific parser"
      (let [parse-fn (fn [value]
                       (string/split value #"\d"))

            source (c/map-source
                     {:countries "usa1uk2germany"})

            middleware (separator-parsing-middleware
                         {:parse-fn parse-fn})

            parameter-value (middleware source :countries)]
        (is (= ["usa" "uk" "germany"] parameter-value))))

    (testing "when using a specific separator"
      (let [source (c/map-source
                     {:countries "usa|uk|germany"})

            middleware (separator-parsing-middleware
                         {:separator "|"})

            parameter-value (middleware source :countries)]
        (is (= ["usa" "uk" "germany"] parameter-value))))

    (testing "when disabling trimming"
      (let [source (c/map-source
                     {:countries " usa , uk , germany "})

            middleware (separator-parsing-middleware
                         {:trim false})

            parameter-value (middleware source :countries)]
        (is (= [" usa " " uk " " germany "] parameter-value))))))

(deftest configuration-definition
  (testing "resolve"
    (testing "resolves all parameters in the specification"
      (let [configuration (c/define-configuration
                            (c/with-source (c/map-source
                                             {:api-username "some-username"
                                              :api-port     "5000"}))
                            (c/with-parameter :api-username)
                            (c/with-parameter :api-port
                              :type :integer))]
        (is (= {:api-username "some-username"
                :api-port     5000}
              (c/resolve configuration)))))

    (testing "resolves from multiple sources"
      (with-redefs
       [clojure.core/slurp (fn [path]
                             (if (= path "path/to/config.yaml")
                               (str
                                 "---\n"
                                 "api_username: \"some-username\"\n"
                                 "api_password: \"some-password\"\n")))]
        (let [configuration (c/define-configuration
                              (c/with-source
                                (c/map-source {:api-port "5000"}))
                              (c/with-source
                                (c/yaml-file-source "path/to/config.yaml"))
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-parameter :api-port
                                :type :integer))]
          (is (= {:api-username "some-username"
                  :api-password "some-password"
                  :api-port     5000}
                (c/resolve configuration))))))

    (testing "can be created from an existing specification"
      (let [specification (c/define-configuration-specification
                            (c/with-parameter :api-username)
                            (c/with-parameter :api-password))
            configuration (c/define-configuration
                            (c/with-source
                              (c/map-source {:api-username "some-username"
                                             :api-password "some-password"
                                             :api-port     "5000"}))
                            (c/with-specification specification)
                            (c/with-parameter :api-port :type :integer))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (c/resolve configuration)))))

    (testing "can be created from multiple existing specifications"
      (let [specification1 (c/define-configuration-specification
                             (c/with-parameter :api-username)
                             (c/with-parameter :api-password))
            specification2 (c/define-configuration-specification
                             (c/with-parameter :api-port :type :integer))
            configuration (c/define-configuration
                            (c/with-source
                              (c/map-source {:api-username "some-username"
                                             :api-password "some-password"
                                             :api-port     "5000"}))
                            (c/with-specification specification1)
                            (c/with-specification specification2))]
        (is (= {:api-username "some-username"
                :api-password "some-password"
                :api-port     5000}
              (c/resolve configuration)))))

    (testing "scopes key functions when defined on specifications"
      (let [specification1 (c/define-configuration-specification
                             (c/with-parameter :db-username)
                             (c/with-key-fn (remove-prefix :db))
                             (c/with-key-fn (add-prefix :postgres)))
            specification2 (c/define-configuration-specification
                             (c/with-parameter :database-password)
                             (c/with-key-fn (remove-prefix :database))
                             (c/with-key-fn (add-prefix :postgres)))
            configuration (c/define-configuration
                            (c/with-source {:db-username       "some-username"
                                            :database-password "some-password"})
                            (c/with-specification specification1)
                            (c/with-specification specification2))]
        (is (= {:postgres-username "some-username"
                :postgres-password "some-password"}
              (c/resolve configuration)))))

    (testing "uses provided key functions"
      (let [configuration (c/define-configuration
                            (c/with-source
                              (c/map-source {:api-username "some-username"
                                             :api-password "some-password"
                                             :api-port     "5000"}))
                            (c/with-parameter :api-username)
                            (c/with-parameter :api-password)
                            (c/with-parameter :api-port :type :integer)
                            (c/with-key-fn (remove-prefix :api))
                            (c/with-key-fn (add-prefix :service)))]
        (is (= {:service-username "some-username"
                :service-password "some-password"
                :service-port     5000}
              (c/resolve configuration)))))

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
            (c/define-configuration
              (c/with-source
                (c/map-source
                  {:api-credentials
                   "{\"user\": \"james\",\"pass\": \"X4ftRd32\"}"
                   :api-timeout
                   "10000"})
                (c/with-middleware (json-parsing-middleware))
                (c/with-middleware password-masking-middleware))
              (c/with-parameter :api-credentials :type :map)
              (c/with-parameter :api-timeout :type :integer))]
        (is (= {:api-timeout     10000
                :api-credentials {:user "james"
                                  :pass "********"}}
              (c/resolve configuration))))))

  (testing "merge"
    (testing "resolves parameters across all definitions"
      (let [configuration-1 (c/define-configuration
                              (c/with-source
                                (c/map-source {:api-username "api-username"
                                               :api-password "api-password"}))
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password))
            configuration-2 (c/define-configuration
                              (c/with-source
                                (c/map-source {:db-username "db-username"
                                               :db-password "db-password"}))
                              (c/with-parameter :db-username)
                              (c/with-parameter :db-password))
            merged (c/merge configuration-1 configuration-2)]
        (is (= {:api-username "api-username"
                :api-password "api-password"
                :db-username  "db-username"
                :db-password  "db-password"}
              (c/resolve merged)))))

    (testing "uses only sources defined within each configuration definition"
      (let [configuration-1 (c/define-configuration
                              (c/with-source
                                (c/map-source
                                  {:api-username "api-username"
                                   :api-password "api-password"
                                   :db-username  "other-db-username"
                                   :db-password  "other-db-password"}))
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password))
            configuration-2 (c/define-configuration
                              (c/with-source
                                (c/map-source
                                  {:api-username "other-api-username"
                                   :api-password "other-api-password"
                                   :db-username  "db-username"
                                   :db-password  "db-password"}))
                              (c/with-parameter :db-username)
                              (c/with-parameter :db-password))
            merged (c/merge configuration-1 configuration-2)]
        (is (= {:api-username "api-username"
                :api-password "api-password"
                :db-username  "db-username"
                :db-password  "db-password"}
              (c/resolve merged)))))

    (testing "scopes key functions to each configuration definition"
      (let [configuration-1 (c/define-configuration
                              (c/with-source
                                (c/map-source
                                  {:api-username "api-username"
                                   :api-password "api-password"}))
                              (c/with-parameter :api-username)
                              (c/with-parameter :api-password)
                              (c/with-key-fn (remove-prefix :api))
                              (c/with-key-fn (add-prefix :service)))
            configuration-2 (c/define-configuration
                              (c/with-source
                                (c/map-source
                                  {:db-username "db-username"
                                   :db-password "db-password"}))
                              (c/with-parameter :db-username)
                              (c/with-parameter :db-password)
                              (c/with-key-fn (remove-prefix :db))
                              (c/with-key-fn (add-prefix :database)))
            merged (c/merge configuration-1 configuration-2)]
        (is (= {:service-username  "api-username"
                :service-password  "api-password"
                :database-username "db-username"
                :database-password "db-password"}
              (c/resolve merged)))))))
