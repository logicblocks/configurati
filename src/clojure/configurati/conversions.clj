(ns configurati.conversions)

(defmulti convert-to (fn [type value] type))

(defmethod convert-to :integer [_ value]
  (if value (Integer/parseInt (str value)) nil))

(defmethod convert-to :string [_ value]
  (if value (String/valueOf value) nil))

(defmethod convert-to :default [_ value]
  value)
