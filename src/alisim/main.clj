(ns alisim.main
  (require [alisim.handler :as handler]
           [alisim.commons.caseutil :as cu]
           [alisim.commons.signature :as sign]
           [alisim.commons.packutil :as pu]
           [alisim.commons.rpc :as rpc]
           [alisim.commons.config :as cfg]
           [clojure.xml :as xml]
           )
  (:import [javax.swing JFrame JButton JOptionPane JComboBox JPanel JLabel JSeparator JSplitPane JList JScrollPane JTextPane JTextArea JTextField JTabbedPane]
           [javax.swing BoxLayout BorderFactory]
           [javax.swing.text StyledDocument SimpleAttributeSet StyleConstants]
           [java.awt Component Container Dimension]
           [java.awt BorderLayout GridLayout GridBagLayout GridBagConstraints]
           [java.awt.event ActionListener]
           [java.awt Color]
           )
  (:gen-class)
  )

(defn create-field-widget [field-desc]
  (let [value-fn (:value-fn field-desc)]
    (cond
     (seq? value-fn) (JComboBox. (java.util.Vector. value-fn))
     (nil? value-fn) (JTextField.)
     :else (JTextField.)
     ))
  )

(defn add-field-widget [pane field-desc]
  (let [widget (create-field-widget field-desc)]
    (let [c (GridBagConstraints.)]
      (set! (.anchor c) GridBagConstraints/EAST)
      (set! (.gridwidth c) GridBagConstraints/RELATIVE)
      (set! (.fill c) GridBagConstraints/NONE)
      (.add pane (JLabel. (str (:desc field-desc) ": ")) c))
    (let [c (GridBagConstraints.)]
      (set! (.anchor c) GridBagConstraints/EAST)
      (set! (.gridwidth c) GridBagConstraints/REMAINDER) ;; end of row
      (set! (.fill c) GridBagConstraints/HORIZONTAL)
      (set! (.weightx c) 1.0)
      (.add pane widget c))
    widget
    )
  )

(defn create-case-pane [case field-def-map dicts]
  (let [pane (JPanel. (GridBagLayout.))
        widgets (doall (for [field (filter #(= (:show (xml/attrs %1)) "true") (xml/content ((xml/content (:value case)) 0)))]
                         (let [name (:name (xml/attrs field))]
                           [name (add-field-widget pane (field-def-map name))]
                           )))]
    {:pane pane :widget-map (into {} widgets)}
    )
  )

(defn insert-string [text-pane offset str attr] (-> (.getStyledDocument text-pane)
                                                    (.insertString offset str attr)))
(defn append-string [text-pane str & [attr]] (let [doc (.getStyledDocument text-pane)]
                                           (.insertString doc (.getLength doc) str attr)))
(defn get-text [text-pane] (let [doc (.getStyledDocument text-pane)] (.getText doc 0 (.getLength doc))))
(defn clear-text [text-pane] (let [doc (.getStyledDocument text-pane)] (.remove doc 0 (.getLength doc))))

(defn create-gui [cases field-def-map dicts]
  (let [pane (JPanel. (BorderLayout.))
        left-pane (JPanel. (BorderLayout.))
        tabbed-pane (JTabbedPane.)
        _ (.setPreferredSize tabbed-pane (Dimension. 500 155))
        _ (.setMinimumSize tabbed-pane (Dimension. 500 155))
        tabs (doall (for [case cases]
                   (let [tab (create-case-pane case field-def-map dicts)]
                     (.addTab tabbed-pane (:key case) (:pane tab))
                     (assoc tab :key (:key case) :case (:value case)))))
        send-btn (JButton. "send message")
        clear-btn (JButton. "clear logs")
        _ (.add left-pane tabbed-pane BorderLayout/CENTER)
        _ (let [tmp-pane (JPanel. (BorderLayout.))]
            (.add tmp-pane send-btn BorderLayout/LINE_START)
            (.add tmp-pane clear-btn BorderLayout/LINE_END)
            (.add left-pane tmp-pane BorderLayout/PAGE_END))
        log-pane (doto (JTextPane.) (.setEditable false))
        right-pane (doto (JScrollPane. log-pane)
                     (.setBorder (BorderFactory/createCompoundBorder (BorderFactory/createTitledBorder "logs")
                                                                     (BorderFactory/createEmptyBorder 5 5 5 5)))
                     (.setPreferredSize (Dimension. 600 600)))
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT left-pane right-pane)
        error-attr (let [attr (SimpleAttributeSet.)]
                     (StyleConstants/setBold attr true)
                     (StyleConstants/setForeground attr (Color. 255 0 0))
                     attr)
        ok-attr (let [attr (SimpleAttributeSet.)]
                     (StyleConstants/setBold attr true)
                     (StyleConstants/setForeground attr (Color. 0 255 0))
                     attr)
        ]
;;    (.setLayout pane (BorderLayout.))
    (.add pane split-pane)
    (.addActionListener send-btn
                        (proxy [ActionListener] []
                          (actionPerformed [evt]
                            ;; (JOptionPane/showMessageDialog nil,
                            ;;                                (str "<html>Hello from <b>Clojure</b>. Button "
                            ;;                                     (.getActionCommand evt)
                            ;;                                     " clicket.")))
                            (let [tab (nth tabs (.getSelectedIndex tabbed-pane))
                                  kv-map (reduce (fn [kvs [name widget]]
                                                   (cond
                                                    (= (type widget) JTextField) (assoc kvs name (.getText ^JTextField widget))
                                                    (= (type widget) JComboBox) (assoc kvs name (:key (.getSelectedItem ^JComboBox widget)))
                                                    :else kvs
                                                    ))
                                                 {}
                                                 (:widget-map tab)
                                                 )
                                  tag (-> (:case tab) xml/content first xml/attrs (:tag))
                                  fields (->> (:case tab)
                                              xml/content
                                              first
                                              xml/content
                                              (filter #(= (:tag %1) :field))
                                              (map #(let [attr-map (xml/attrs %1)]
                                                      (assoc attr-map :value-fn (:value-fn (field-def-map (:name attr-map)))))))
                                  xml-request (pu/make-xml tag fields kv-map)
                                  _ (println xml-request)
                                  signed-request (sign/generate-xml-signature xml-request tag)
                                  _ (append-string log-pane (str "Requst:\n" signed-request))
                                  xml-response (rpc/call-service (cfg/get-config :bank-url) signed-request (cfg/get-config :do-urlencode))
                                  _ (append-string log-pane (str "\n\n==========================================\nResponse:\n" xml-response "\n\n"))
                                  ]
                              (if (sign/validate-xml-signature xml-response)
                                (append-string log-pane "\n\n********signature verify ok*********\n" ok-attr)
                                (append-string log-pane "\n\n********signature verify failed*******\n" error-attr))
                              )
                            )))
    (.addActionListener clear-btn
                        (proxy [ActionListener] []
                          (actionPerformed [evt] (clear-text log-pane)
                            )))
    pane)
  )

(defn -main [& args]
  (cfg/load-config "./config.clj")
  (sign/load-key (cfg/get-config :private-key-file) (cfg/get-config :public-key-file) (cfg/get-config :password))

  (handler/start-server (cfg/get-config :port))

  (let [cases (cu/load-cases "./cases")
        dicts (cu/load-dictionary "./dictionary")
        field-def-map (cu/load-field-def "./field-def.xml" dicts)
        frame (JFrame. "Alipay Simulator - version 0.1")
        ]
    (.add frame (create-gui cases field-def-map dicts))
    (doto frame
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      .pack
      (.setVisible true))
    )
  (read-line)

  (handler/stop-server)
)

