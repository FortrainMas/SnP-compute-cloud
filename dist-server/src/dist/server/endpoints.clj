(ns dist.server.endpoints
  (:require [dist.server.scheduler :as scheduler]))

(defn submit-task [task]
  (println "submit task")
  (scheduler/submit-task task))

(defn steal-task []
  (scheduler/steal-task))

(defn complete-task [data]
  (scheduler/complete-task data))

(defn heartbeat [{:keys [id]}]
  (scheduler/heartbeat id)
  :ok)
