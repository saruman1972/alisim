(ns alisim.handler
  (:use compojure.core
        ring.server.standalone
        ring.adapter.jetty)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as rur]
            [clojure.java.io :as io]
            [alisim.commons.signature :as sign]
            [alisim.commons.config :as cfg]
            [alisim.routes.batch-query-notify :as bqn]
            )
  (:gen-class)
)

(defn init []
  (println "starting batch query notify handler")
  (cfg/load-config "./config.clj")
  (sign/load-key (cfg/get-config :private-key-file) (cfg/get-config :public-key-file))
)

(defn destroy []
  (println "shuting down batch query notify handler")
)

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/" request (-> (rur/response (bqn/batch-query-notify-routes request))
                        (rur/header "Allow" "POST")))
  (route/resources "/")
  (route/not-found "Not Found"))

(defn wrap-xml-response [handler]
  (fn [req]
(println req)
;;(println "request:")
;;(println (slurp (:body req)))
    (let [response (handler req)
          body* (.getBytes (:body response) "utf-8")
          body-length (count body*)]
      (-> response
          (assoc :body (io/input-stream body*))                                                                                                              
          (rur/content-type "application/xml; charset=utf-8")
          (rur/header "Content-Length" body-length))
      ))
)

(def app
  (-> (handler/api app-routes)
      (wrap-xml-response)))

;;  (handler/site app-routes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server (atom nil))

(defn get-handler []
  ;; #'app expands to (var app) so that when we reload our code,
  ;; the server is forced to re-resolve the symbol in the var
  ;; rather than having its own copy. When the root binding
  ;; changes, the server picks it up without having to restart.
#'app)
;;  (-> #'app
    ; Makes static assets in $PROJECT_DIR/resources/public/ available.
;;    (wrap-file "resources")
    ; Content-Type, Content-Length, and Last Modified headers for files in body
;;    (wrap-file-info)))

(defn start-server
  "used for starting the server in development mode from REPL"
  [& [port]]
  (let [port (if port port 9080)]
    (reset! server
            ;; (serve (get-handler)
            ;;        {:port port
            ;;         :init init
            ;;         :auto-reload? true
            ;;         :destroy destroy
            ;;         :join true}))
            (run-jetty (get-handler)
                   {:port port
                    :init init
                    :auto-reload? true
                    :destroy destroy
                    :join? false}))
    (println (str "You can view the site at http://localhost:" port))))

(defn stop-server []
  (.stop @server)
  (reset! server nil))

