(ns configurati.conversions)

(defmulti convert-to (fn [type _] type))

(defmethod convert-to :integer [_ value]
  (if (some? value)
    (Integer/parseInt (str value))
    nil))

(defmethod convert-to :string [_ value]
  (if (some? value)
    (String/valueOf value)
    nil))

(defmethod convert-to :default [_ value]
  value)
