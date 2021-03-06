(ns alisim.commons.packutil
  (:require [alisim.commons.config :as cfg])
  )

(def message-id (atom (System/currentTimeMillis)))

(defn tag-value [field kvs]
  (let [value (cond
               (= (:show field) "true") (kvs (:name field))
               (:value field) (:value field))]
    (cond
     (fn? (:value-fn field)) ((:value-fn field) field value)
     :else value
     ))
  )

(defn make-xml [msg-type fields kvs & [msgid]]
  (swap! message-id (fn [_] (System/currentTimeMillis)))
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       "<Finance>\n"
       "\t<Message id=\"" (if msgid msgid @message-id) "\">\n"
       "\t\t<" msg-type " id=\"" msg-type "\">\n"
       (reduce #(str %1 "\t\t\t<" (:name %2) ">" (tag-value %2 kvs) "</" (:name %2) ">\n")
               ""
               fields)
       "\t\t</" msg-type ">\n"
       "\t</Message>\n</Finance>\n"
    )
  )


