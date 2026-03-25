(ns dist.server.scheduler
  (:import
   [java.util UUID]
   [java.util.concurrent ConcurrentLinkedQueue Executors TimeUnit]))

(def pending-queue
  (ConcurrentLinkedQueue.))

(def in-flight (atom {}))

(def task-promises (atom {}))

(def scheduler
  (Executors/newScheduledThreadPool 1))

(def TIMEOUT_MS 10000)

(defn now []
  (System/currentTimeMillis))

(defn submit-task [task]
  (let [id (str (UUID/randomUUID))
        p  (promise)]
    (swap! task-promises assoc id p)
    (.add pending-queue {:id id :task task})
    {:id id :promise p}))

(defn steal-task []
  (loop []
    (when-let [task (.poll pending-queue)]
      (let [id (:id task)]
        (if-not (contains? @task-promises id)
          ;; Stale task (already completed/cancelled), skip it.
          (recur)
          (let [deadline (+ (now) TIMEOUT_MS)]
            (swap! in-flight assoc id (assoc task :deadline deadline))

            (.schedule scheduler
                       (fn []
                         (when-let [t (get @in-flight id)]
                           (when (< (:deadline t) (now))
                             (println "Requeue task" id)
                             (swap! in-flight dissoc id)
                             (.add pending-queue (dissoc t :deadline)))))
                       TIMEOUT_MS
                       TimeUnit/MILLISECONDS)

            task))))))

(defn heartbeat [id]
  (println "heartbeat" id)
  (swap! in-flight update id
         (fn [t]
           (when t
             (assoc t :deadline (+ (now) TIMEOUT_MS))))))

(defn complete-task [{:keys [id result]}]
  (println "complete task" id)

  (when-let [p (get @task-promises id)]
    (deliver p result)

    (swap! task-promises dissoc id)
    (swap! in-flight dissoc id)

    :ok))
