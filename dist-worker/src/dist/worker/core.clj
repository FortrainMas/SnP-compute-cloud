(ns dist.worker.core
  (:require
     [ring.adapter.jetty :refer [run-jetty]]
     [clojure.edn :as edn]
     [dist.worker.executor :as executor]
     [dist.worker.scheduler :as scheduler]
     [dist.worker.submit :as submit]))

(defn worker-loop [id]

  (let [local-q (scheduler/queues id)]

    (while true

      (if-let [task (.pollLast local-q)]

        (submit/submit-result (:id task) (executor/execute-task (:task task)))


        (if-let [task (scheduler/steal-task id)]

            (submit/submit-result (:id task) (executor/execute-task (:task task)))

            (if-let [task (scheduler/steal-from-cluster)]

              (submit/submit-result (:id task) (executor/execute-task (:task task)))

              (Thread/sleep 1)))))))

(defn start-workers []

  (doseq [i (range scheduler/threads)]

    (let [t (Thread. #(worker-loop i))]

      (.setName t (str "worker-thread-" i))
      (.start t))))


(defn -main []
  (start-workers))
