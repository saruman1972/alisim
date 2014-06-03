(defproject alisim "0.3.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [ring-server "0.3.1"]
                 [commons-codec/commons-codec "1.7"]
                 [org.apache.httpcomponents/httpclient "4.3.2"]
                 [org.apache.commons/commons-io "1.3.2"]
                ]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler alisim.handler/app
         :init alisim.handler/init
         :destroy alisim.handler/destroy
         :port 4000}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}}
  :main alisim.main
)
