(ns dist.worker.executor)

(defn load-registry [registry]

  (doseq [[name code] registry]

    (let [f (eval code)]

      (intern *ns* name f))))


(defn execute-task [task]
    (println "executing task")
    (println task)

  (load-registry (:registry task))

  (case (:type task)

    :call
    (let [f (resolve (:fn task))]
      (apply (deref f) (:args task)))

    :map
    (let [f (resolve (:fn task))]
      (doall (map (deref f) (:args task))))

    :filter
    (let [f (resolve (:fn task))]
      (doall (filter (deref f) (:args task))))

    :reduce
    (let [f (resolve (:fn task))]
      (reduce (deref f) (:args task)))

    (throw (Exception. "Unknown task type"))))
