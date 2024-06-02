(ns configurati.conversions)

(defmulti convert-to (fn [type _] type))

(defmethod convert-to :integer [_ value]
  (when (some? value) (Integer/parseInt (str value))))

(defmethod convert-to :string [_ value]
  (when (some? value) (String/valueOf value)))

(defmethod convert-to :boolean [_ value]
  (cond
    (nil? value) nil
    (or (= "true" value) (true? value)) true
    (or (= "false" value) (false? value)) false
    :else
    (throw (ex-info "Can't convert value to boolean"
             {:value    value
              :accepted #{true "true" false "false"}}))))

(defmethod convert-to :default [_ value]
  value)
