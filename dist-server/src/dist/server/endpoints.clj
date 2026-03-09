(ns dist.server.endpoints)

(defonce task-queue (atom clojure.lang.PersistentQueue/EMPTY))
(defonce task-promises (atom {}))
(defonce workers (atom #{}))

(defn submit-task [task]

  (let [id (str (java.util.UUID/randomUUID))
        p  (promise)]

    (println "task")
    (swap! task-promises assoc id p)
    (println "swapped")
    (swap! task-queue conj
           {:id id
            :task task})
    (println "res")
    {:task-id id
     :promise p}))

(defn steal-task []
  (let [q @task-queue]

    (when (seq q)

      (let [task (peek q)]

        (swap! task-queue pop)

        task))))

(defn complete-task [{:keys [id result]}]
    (println "complete task")

  (when-let [p (get @task-promises id)]

    (deliver p result)

    (swap! task-promises dissoc id)

    :ok))
