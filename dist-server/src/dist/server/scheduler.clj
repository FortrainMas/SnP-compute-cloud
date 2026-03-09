(ns dist.server.scheduler)

(def task-queue
  (java.util.concurrent.ConcurrentLinkedQueue.))

(defn submit-task [task]
    (println "added task to queue")
  (.add task-queue task))

(defn poll-task []
  (.poll task-queue))
