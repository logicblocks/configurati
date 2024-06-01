(ns configurati.conversions-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [configurati.conversions :as conf-conv]))

(deftest integer-conversion
  (testing "converts integer string to integer"
    (is (= 5 (conf-conv/convert-to :integer "5")))
    (is (= 0 (conf-conv/convert-to :integer "0")))
    (is (= -5 (conf-conv/convert-to :integer "-5")))
    (is (= Integer/MAX_VALUE
          (conf-conv/convert-to :integer (str Integer/MAX_VALUE))))
    (is (= Integer/MIN_VALUE
          (conf-conv/convert-to :integer (str Integer/MIN_VALUE)))))
  (testing "leaves integers untouched"
    (is (= 5 (conf-conv/convert-to :integer 5)))
    (is (= 0 (conf-conv/convert-to :integer 0)))
    (is (= -5 (conf-conv/convert-to :integer -5)))
    (is (= Integer/MAX_VALUE
          (conf-conv/convert-to :integer Integer/MAX_VALUE)))
    (is (= Integer/MIN_VALUE
          (conf-conv/convert-to :integer Integer/MIN_VALUE))))
  (testing "throws for non-integral values"
    (is (thrown? Exception
          (conf-conv/convert-to :integer "hello there")))
    (is (thrown? Exception
          (conf-conv/convert-to :integer true)))
    (is (thrown? Exception
          (conf-conv/convert-to :integer [1, 2, 3])))))

(deftest string-conversion
  (testing "converts values to string"
    (is (= "5" (conf-conv/convert-to :string 5)))
    (is (= "2.2" (conf-conv/convert-to :string 2.2)))
    (is (= "true" (conf-conv/convert-to :string true)))
    (is (= "[1 2 3]" (conf-conv/convert-to :string [1 2 3]))))
  (testing "leaves string untouched"
    (is (= "hello" (conf-conv/convert-to :string "hello")))
    (is (= "5" (conf-conv/convert-to :string "5")))
    (is (= "true" (conf-conv/convert-to :string "true")))))

(deftest boolean-conversion
  (testing "converts values to boolean"
    (is (= true (conf-conv/convert-to :boolean "true")))
    (is (= false (conf-conv/convert-to :boolean "false"))))
  (testing "leaves booleans untouched"
    (is (= true (conf-conv/convert-to :boolean true)))
    (is (= false (conf-conv/convert-to :boolean false))))
  (testing "throws for non-boolean values"
    (is (thrown? Exception
          (conf-conv/convert-to :boolean "spinach"))))
  (is (thrown? Exception
        (conf-conv/convert-to :boolean 26))))
