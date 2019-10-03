(ns configurati.conversions)

(defmulti convert-to (fn [type _] type))

(defmethod convert-to :integer [_ value]
  (when (some? value) (Integer/parseInt (str value))))

(defmethod convert-to :string [_ value]
  (when (some? value) (String/valueOf value)))

(defmethod convert-to :default [_ value]
  value)
