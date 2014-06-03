(ns alisim.routes.batch-query-notify
  (:require [alisim.commons.signature :as sign]
            [alisim.commons.caseutil :as cu]
            [alisim.commons.packutil :as pu]
            [clojure.xml :as xml])
  (:import [javax.swing JFrame JButton JOptionPane SwingUtilities]
           [javax.swing BoxLayout BorderFactory]
           [java.awt Component Container Dimension]
           [java.awt BorderLayout GridLayout GridBagLayout GridBagConstraints]
           [java.awt.event ActionListener]
           [java.awt Color]
           [java.io ByteArrayInputStream])
  )

(defn get-message-id [xml-req]
  (->> (xml/parse (ByteArrayInputStream. (.getBytes xml-req "UTF-8")))
      (xml/content)
      (filter #(= :Message (:tag %1)))
      (first)
      (xml/attrs)
      (:id))
  )

(defn batch-query-notify-routes [request]
  (let [tag "TQRNotifyResp"
        xml-req (slurp (:body request))
        message-id (get-message-id xml-req)
        ;;valid-signature (sign/validate-xml-signature request)
        fields [{:name "version", :value-fn (:value-fn (@cu/field-def-map "version"))}
                {:name "instId", :value-fn (:value-fn (@cu/field-def-map "instId"))}
                {:name "certId", :value-fn (:value-fn (@cu/field-def-map "certId"))}]
        ]
    (SwingUtilities/invokeLater (JOptionPane/showMessageDialog nil, (str "Receive Notify:\n" xml-req)))
    (-> (pu/make-xml tag fields {} message-id)
        (sign/generate-xml-signature tag))
    )
  )

