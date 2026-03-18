(ns dist.server.core
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [clojure.edn :as edn]
   [dist.server.endpoints :as endpoints]))

(defn handler [req]
  (let [uri (:uri req)
        body (slurp (:body req))
        data (when (seq body) (edn/read-string body))]

    (case uri

      "/submit"
      (let [{:keys [promise]} (endpoints/submit-task data)
            result @promise]
        {:status 200
         :body (pr-str result)})

      "/steal"
      {:status 200
       :body (pr-str (endpoints/steal-task))}

      "/result"
      (do
        (endpoints/complete-task data)
        {:status 200
         :body "ok"})

      "/heartbeat"
      (do
        (endpoints/heartbeat data)
        {:status 200
         :body "ok"})

      {:status 404
       :body "not found"})))

(defn -main []
  (println "Starting server on 8080")
  (run-jetty handler {:port 8080}))
