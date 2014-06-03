(ns alisim.commons.config
  (:require [clojure.java.io :as io])
  (:import [java.io PushbackReader])
  )

(def serial-no (atom 0))

(def config (atom {}))

(defn load-config [filename]
  (let [cfg (with-open [r (io/reader filename)]
              (read (PushbackReader. r)))]
    (reset! config cfg)
    (reset! serial-no (:serialNo cfg))
    )
  )

(defn get-config [key] (@config key))

(defn next-serial-no [] (swap! serial-no inc))
