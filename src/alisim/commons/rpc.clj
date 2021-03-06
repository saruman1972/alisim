(ns alisim.commons.rpc
  (import [org.apache.http HttpEntity HttpResponse]
          [org.apache.http.client HttpClient]
          [org.apache.http.client.methods HttpPost]
          [org.apache.http.entity InputStreamEntity StringEntity]
          [org.apache.http.impl.client DefaultHttpClient]
          [org.apache.http.util EntityUtils]
          [org.apache.commons.io IOUtils]
          [java.io ByteArrayInputStream]
          [java.net URLDecoder URLEncoder]
          [org.apache.commons.codec.binary Hex]
          ))

(defn call-service
  "call bank service via xml over http."
  [url xml-string & [do-urlencode]]
  (let [http-client (DefaultHttpClient.)
        http-post (HttpPost. url )
        ;; req-entity (InputStreamEntity.
        ;;             (ByteArrayInputStream. (.getBytes xml-string "utf-8"))
        ;;             -1)
        req-entity (StringEntity. (if (= do-urlencode true)
                                    (URLEncoder/encode xml-string "UTF-8")
                                    xml-string)
                                  "UTF-8")
        ]
    (doto req-entity
      (.setContentType "application/xml; charset=UTF-8")
;      (.setChunked true)
      )
    (.setEntity http-post req-entity)
    (println (str "Executing request " (.getRequestLine http-post)))
    (try
      (let [http-resp (.execute http-client http-post)
            res-entity (.getEntity http-resp)
            status-line (.getStatusLine http-resp)
            status-code (.getStatusCode status-line)
            _ (println "---------------------------------------")
            _ (println status-line)
            xml-resp (if (or (= 200 status-code) (= 201 status-code))
                       (if-not (nil? res-entity)
                         (let [content (IOUtils/toString (.getContent res-entity) "UTF-8")]
                           (if (= do-urlencode true)
                             (URLDecoder/decode content "UTF-8")
                             content)))
                       ("unexpected status code:" status-code))
            ]
        (EntityUtils/consume res-entity)
        xml-resp
        )
      (catch Exception e (str "Caught exception: " (.getMessage e)))
      (finally (.. http-client
                   (getConnectionManager)
                   (shutdown))))
    )
  )






