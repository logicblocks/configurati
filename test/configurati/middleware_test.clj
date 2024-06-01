(ns configurati.middleware-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [configurati.core :as conf]
   [configurati.key-fns :as conf-kfs]
   [configurati.middleware :as conf-mdlw]
   [jason.convenience :as json-conv]
   [jason.core :as json]))

(deftest json-parsing-middleware
  (testing "by default applies to all parameters"
    (let [issuer-1-value {:url      "https://issuer-1.example.com"
                          :audience "https://service-1.example.com"}
          issuer-2-value {:url      "https://issuer-2.example.com"
                          :audience "https://service-2.example.com"}
          source (conf/map-source
                   {:issuer-1 (json-conv/->wire-json issuer-1-value)
                    :issuer-2 (json-conv/->wire-json issuer-2-value)})

          middleware (conf-mdlw/json-parsing-middleware)

          parameter-1-value (middleware source :issuer-1)
          parameter-2-value (middleware source :issuer-2)]
      (is (= issuer-1-value parameter-1-value))
      (is (= issuer-2-value parameter-2-value))))

  (testing "passes through nil value when parameter not available in source"
    (let [source (conf/map-source
                   {:issuer
                    (json-conv/->wire-json
                      {:url      "https://issuer-1.example.com"
                       :audience "https://service-1.example.com"})})

          middleware (conf-mdlw/json-parsing-middleware)

          parameter-value (middleware source :other)]
      (is (nil? parameter-value))))

  (testing "when applied to specific parameters"
    (let [issuer-value {:url      "https://issuer-1.example.com"
                        :audience "https://service-1.example.com"}
          timeout-value 10000

          source (conf/map-source
                   {:issuer  (json-conv/->wire-json issuer-value)
                    :timeout timeout-value})

          middleware (conf-mdlw/json-parsing-middleware
                       {:only [:issuer]})

          issuer-parameter-value (middleware source :issuer)
          timeout-parameter-value (middleware source :timeout)]
      (is (= issuer-value issuer-parameter-value))
      (is (= timeout-value timeout-parameter-value))))

  (testing "when using a specific JSON parser"
    (let [json-parser (json/new-json-decoder)

          source (conf/map-source
                   {:issuer (json-conv/->wire-json
                              {:url      "https://issuer-1.example.com"
                               :audience "https://service-1.example.com"})})

          middleware (conf-mdlw/json-parsing-middleware
                       {:parse-fn json-parser})

          parameter-value (middleware source :issuer)]
      (is (= {"url"      "https://issuer-1.example.com"
              "audience" "https://service-1.example.com"}
            parameter-value))))

  (testing "when using a specific key function"
    (let [key-fn (fn [key] (keyword (str (name key) "-modified")))

          source (conf/map-source
                   {:issuer (json-conv/->wire-json
                              {:url      "https://issuer-1.example.com"
                               :audience "https://service-1.example.com"})})

          middleware (conf-mdlw/json-parsing-middleware
                       {:key-fn key-fn})

          parameter-value (middleware source :issuer)]
      (is (= {:url-modified      "https://issuer-1.example.com"
              :audience-modified "https://service-1.example.com"}
            parameter-value)))))

(deftest separator-parsing-middleware
  (testing "by default applies to all parameters on comma"
    (let [source (conf/map-source
                   {:countries "usa, uk, germany"
                    :roles     "admin,support"})

          middleware (conf-mdlw/separator-parsing-middleware)

          countries-parameter-value (middleware source :countries)
          roles-parameter-value (middleware source :roles)]
      (is (= ["usa" "uk" "germany"] countries-parameter-value))
      (is (= ["admin" "support"] roles-parameter-value))))

  (testing "passes through nil value when parameter not available in source"
    (let [source (conf/map-source
                   {:countries "usa,uk,germany"})

          middleware (conf-mdlw/separator-parsing-middleware)

          parameter-value (middleware source :other)]
      (is (nil? parameter-value))))

  (testing "when applied to specific parameters"
    (let [source (conf/map-source
                   {:client    "Company, UK"
                    :countries "usa,uk,germany"})

          middleware (conf-mdlw/separator-parsing-middleware
                       {:only [:countries]})

          client-parameter-value (middleware source :client)
          countries-parameter-value (middleware source :countries)]
      (is (= ["usa" "uk" "germany"] countries-parameter-value))
      (is (= "Company, UK" client-parameter-value))))

  (testing "when using a specific parser"
    (let [parse-fn (fn [value]
                     (string/split value #"\d"))

          source (conf/map-source
                   {:countries "usa1uk2germany"})

          middleware (conf-mdlw/separator-parsing-middleware
                       {:parse-fn parse-fn})

          parameter-value (middleware source :countries)]
      (is (= ["usa" "uk" "germany"] parameter-value))))

  (testing "when using a specific separator"
    (let [source (conf/map-source
                   {:countries "usa|uk|germany"})

          middleware (conf-mdlw/separator-parsing-middleware
                       {:separator "|"})

          parameter-value (middleware source :countries)]
      (is (= ["usa" "uk" "germany"] parameter-value))))

  (testing "when disabling trimming"
    (let [source (conf/map-source
                   {:countries " usa , uk , germany "})

          middleware (conf-mdlw/separator-parsing-middleware
                       {:trim false})

          parameter-value (middleware source :countries)]
      (is (= [" usa " " uk " " germany "] parameter-value)))))

(deftest parameter-name-transforming-middleware
  (testing "transforms parameter name prior to looking up in source"
    (let [source (conf/map-source
                   {:thing-one 1
                    :thing-two 2})

          middleware (conf-mdlw/parameter-name-transforming-middleware
                       {:transform-fn (conf-kfs/add-prefix :thing)})]
      (is (= 1 (middleware source :one)))
      (is (= 2 (middleware source :two)))))

  (testing
   "only transforms parameter names satisfying :only option when present"
    (let [source (conf/map-source
                   {:thing-one 1
                    :two       2})

          middleware (conf-mdlw/parameter-name-transforming-middleware
                       {:transform-fn (conf-kfs/add-prefix :thing)
                        :only         #{:one}})]
      (is (= 1 (middleware source :one)))
      (is (= 2 (middleware source :two))))))
