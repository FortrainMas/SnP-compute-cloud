(ns dist.worker.core
  (:require
     [ring.adapter.jetty :refer [run-jetty]]
     [clojure.edn :as edn]
     [dist.worker.executor :as executor]
     [dist.worker.scheduler :as scheduler]
     [dist.worker.rpc :as rpc]))

(defn worker-loop [id]

  (while true
    (if-let [task (scheduler/steal-from-cluster)]

      (let [task-id (:id task)
            stop-flag (atom false)
            hb (rpc/start-heartbeat task-id stop-flag)]

        (try
          (let [result (executor/execute-task (:task task))]
            (rpc/submit-result task-id result))

          (finally
            (reset! stop-flag true))))

      (Thread/sleep 100))))

(defn start-workers []

  (doseq [i (range scheduler/threads)]

    (let [t (Thread. #(worker-loop i))]

      (.setName t (str "worker-thread-" i))
      (.start t))))


(defn -main []
  (start-workers))
