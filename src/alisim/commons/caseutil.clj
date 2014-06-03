(ns alisim.commons.caseutil
  (require [clojure.xml :as xml]
           [clojure.java.io :as io]
           [clojure.string :as string]
           ) 
  )

(defrecord KeyValueObj [key value]
  Object
  (toString [_] value))

(defn- xml-file-seq [dir]
  (->> (io/file dir)
       (file-seq)
       (filter #(.endsWith (.getName %) ".xml"))))

(defn load-cases [case-dir]
  (->> case-dir
       (xml-file-seq)
       (map #(let [elem (xml/parse (str case-dir "/" (.getName %1)))]
               (KeyValueObj. (:name (xml/attrs elem)) elem)))
       ))

(defn make-dict [elem]
  (->> elem
      (xml/content)
      (map (fn [e] (let [attr-map (xml/attrs e)]
                     (KeyValueObj. (:key attr-map) (:value attr-map))))))
  )

(defn load-dictionary [dic-dir]
  (->> dic-dir
       (xml-file-seq)
       (map #(let [elem (xml/parse (str dic-dir "/" (.getName %1)))]
               [(:name (xml/attrs elem)) (make-dict elem)]))
       (into {})))

(defn find-first [pred coll]
  (first (filter pred coll))
  )

(def date-time-fn
  (fn [attr-map input-value]
    (let [f (if (nil? (:format attr-map)) "yyyyMMdd HH:mm:ss" (:format attr-map))]
      (-> (java.text.SimpleDateFormat. f)
          (.format (System/currentTimeMillis))))
    ))

(def serial-no (atom 0))

(def serial-no-fn
  (fn [attr-map input-value]
    (swap! serial-no inc)
    ))

(defn make-list-fn [tag]
  (fn [attr-map input-value]
    (->> (string/split input-value #" *, *")
         (reduce #(str %1 "<" tag ">" %2 "</" tag ">\n"))))
  )

(defrecord FieldDesc [name desc encode size value-fn])
(defn make-field-desc [elem dicts]
  (let [attr-map (xml/attrs elem)
        name (:name attr-map)
        desc (:desc attr-map)
        encode (first (xml/content (find-first #(= (xml/tag %1) :encode) (xml/content elem))))
        size (first (xml/content (find-first #(= (xml/tag %1) :size) (xml/content elem))))
        value (find-first #(= (xml/tag %1) :value) (xml/content elem))
        value-fn (if (not (nil? value))
                   (let [attr-map (xml/attrs value)]
                     (cond
                      (:calculate attr-map) (cond
                                             (= (:calculate attr-map) "DATE_TIME") date-time-fn
                                             (= (:calculate attr-map) "SERIAL_NO") serial-no-fn)
                      (:choice attr-map) (dicts (:choice attr-map))
                      (:list attr-map) (make-list-fn (:list attr-map))
                      :else (fn [attr-map input-value] (if (nil? input-value)
                                                         (first (xml/content value))
                                                         input-value))
                      )))
        ]
    (FieldDesc. name desc encode size value-fn)
    )
  )

(def field-def-map (atom {}))
(defn load-field-def [field-def-file dicts]
  (let [fdm (->> field-def-file
                 (xml/parse)
                 (xml/content)
                 (map (fn [elem] [(:name (xml/attrs elem)) (make-field-desc elem dicts)]))
                 (into {})
                 )]
    (reset! field-def-map fdm)
    fdm)
  )


