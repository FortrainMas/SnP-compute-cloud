(ns dist.server.core
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [clojure.edn :as edn]
   [dist.server.executor :as executor]))

(defn handler [req]

  (let [body (slurp (:body req))
        task (edn/read-string body)
        result (executor/execute-task task)]

    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str result)}))

(defn -main []

  (println "Starting server on 8080")

  (run-jetty handler {:port 8080}))
