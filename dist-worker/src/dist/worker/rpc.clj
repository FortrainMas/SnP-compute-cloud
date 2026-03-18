(ns dist.worker.rpc
  (:require
   [clj-http.client :as http]
   [clojure.edn :as edn]))

(def server-host "http://localhost:8080")

(defn submit-result [id result]
    (println "submitting task")
  (let [resp (http/post (str server-host "/result")
                        {:body (pr-str {:id id :result result})
                         :headers {"Content-Type" "application/edn"}})]
    (edn/read-string (:body resp))))

(def HEARTBEAT_INTERVAL_MS 3000)

(defn start-heartbeat [task-id stop-flag]
  (future
    (while (not @stop-flag)
      (try
        (http/post (str server-host "/heartbeat")
                   {:body (pr-str {:id task-id})})
        (catch Exception e
          (println "heartbeat failed" task-id)))

      (Thread/sleep HEARTBEAT_INTERVAL_MS))))
