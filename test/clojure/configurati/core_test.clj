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
             {:name :api-username :nilable false :default nil})
          (with-parameter :api-username)))
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable false :default nil})
          (with-parameter :api-username :nilable false)))
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable true :default nil})
          (with-parameter :api-username :nilable true)))
    (is (= (map->ConfigurationParameter
             {:name :api-username :nilable false :default "username"})
          (with-parameter :api-username :default "username"))))

  (testing "defaulting"
    (let [parameter (map->ConfigurationParameter
                      {:name :api-password
                       :nilable false
                       :default "P@55w0rd"})]
      (is (= "P@55w0rd" (default parameter nil))))
    (let [parameter (map->ConfigurationParameter
                      {:name :api-password
                       :nilable false
                       :default nil})]
      (is (= nil (default parameter nil)))))

  (testing "validation"
    (let [parameter (map->ConfigurationParameter
                      {:name :api-username :nilable false :default nil})]
      (is (= {:error :missing :value nil}
            (validate parameter nil)))
      (is (= {:error nil :value "username"}
            (validate parameter "username"))))))

(deftest configuration-specifications
  (testing "evaluate"
    (let [default-identifier "default-identifier"
          specification (configuration-specification
                          (with-parameter :api-username
                            :nilable false)
                          (with-parameter :api-password
                            :nilable false)
                          (with-parameter :api-identifier
                            :default default-identifier))]

      (testing "returns configuration map when no errors occur"
        (let [configuration-map {:api-username   "some-username"
                                 :api-password   "some-password"
                                 :api-identifier "some-identifier"}]
          (is (= configuration-map
                (evaluate specification configuration-map)))))

      (testing "throws exception when non-nilable parameter is nil"
        (let [configuration-map {:api-username   nil
                                 :api-password   "some-password"
                                 :api-identifier "some-identifier"}
              exception (evaluate-and-catch
                          specification configuration-map)]
          (is (= ExceptionInfo (type exception)))
          (is (= "Configuration evaluation failed."
                (.getMessage exception)))
          (is (= {:missing   [:api-username]
                  :original  configuration-map
                  :evaluated (select-keys configuration-map
                               [:api-password :api-identifier])}
                (ex-data exception)))))

      (testing (str "throws exception with all missing parameters when "
                 "multiple non-nilable parameters are nil")
        (let [configuration-map {:api-username   nil
                                 :api-password   nil
                                 :api-identifier "some-identifier"}
              exception (evaluate-and-catch
                          specification configuration-map)]
          (is (= ExceptionInfo (type exception)))
          (is (= "Configuration evaluation failed."
                (.getMessage exception)))
          (is (= {:missing [:api-username
                            :api-password]
                  :original configuration-map
                  :evaluated (select-keys configuration-map
                               [:api-identifier])}
                (ex-data exception)))))

      (testing "returns provided default when parameter is nil"
        (let [configuration-map {:api-username   "some-username"
                                 :api-password   "some-password"
                                 :api-identifier nil}]
          (is (= (merge configuration-map
                   {:api-identifier default-identifier})
                (evaluate specification configuration-map))))))))
