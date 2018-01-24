(ns configurati.core-test
  (:require
    [clojure.test :refer :all]

    [configurati.core :as c])
  (:import [clojure.lang ExceptionInfo]))

(defn validate-and-catch [specification configuration-map]
  (try
    (c/validate specification configuration-map)
    (catch ExceptionInfo e e)))

(deftest configuration-specification
  (testing "validate"
    (let [specification (c/configuration-specification
                          (c/with-parameter :api-username
                            :nilable false)
                          (c/with-parameter :api-password
                            :nilable false)
                          (c/with-parameter :api-identifier
                            :nilable true))]

      (testing "returns map when no validation errors occur"
        (let [configuration-map {:api-username   "some-username"
                                 :api-password   "some-password"
                                 :api-identifier nil}]
          (is (= configuration-map
                (c/validate specification configuration-map)))))

      (testing "throws exception when non-nilable parameter is nil"
        (let [configuration-map {:api-username   nil
                                 :api-password   "some-password"
                                 :api-identifier "some-identifier"}
              exception (validate-and-catch
                          specification configuration-map)]
          (is (= ExceptionInfo (type exception)))
          (is (= "Configuration validation failed."
                (.getMessage exception)))
          (is (= {:missing [:api-username]} (ex-data exception)))))

      (testing (str "throws exception with all missing parameters when "
                 "multiple non-nilable parameters are nil")
        (let [configuration-map {:api-username   nil
                                 :api-password   nil
                                 :api-identifier nil}
              exception (validate-and-catch
                          specification configuration-map)]
          (is (= ExceptionInfo (type exception)))
          (is (= "Configuration validation failed."
                (.getMessage exception)))
          (is (= {:missing [:api-username
                            :api-password]}
                (ex-data exception))))))))

(deftest with-parameter
  (is (=
        {:name :api-username :nilable false}
        (c/with-parameter :api-username)))
  (is (=
        {:name :api-username :nilable false}
        (c/with-parameter :api-username :nilable false)))
  (is (=
        {:name :api-username :nilable true}
        (c/with-parameter :api-username :nilable true))))
