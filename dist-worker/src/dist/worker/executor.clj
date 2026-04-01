(ns dist.worker.executor
  (:require
   [dist.dto :as dto]))

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

    :jvm-call
    (let [_ (dto/assert-required-classes! (:required-classes task))
          f (dto/deserialize-java (:fn-ser task))
          args (mapv dto/deserialize-java (:args-ser task))
          result (dto/invoke-jvm-call (:kind task) f args)]
      (if (:return-serialized? task)
        (dto/serialize-java result)
        result))

    :jvm-reduce-chunk
    (let [_ (dto/assert-required-classes! (:required-classes task))
          f (dto/deserialize-java (:fn-ser task))
          items (mapv dto/deserialize-java (:items-ser task))
          identity-value (dto/deserialize-java (:identity-ser task))
          reduced (if (empty? items)
                    identity-value
                    (if (some? identity-value)
                      (reduce (fn [acc x]
                                (dto/invoke-jvm-call :binary-operator f [acc x]))
                              identity-value
                              items)
                      (reduce (fn [acc x]
                                (dto/invoke-jvm-call :binary-operator f [acc x]))
                              items)))]
      (dto/serialize-java reduced))

    (throw (Exception. "Unknown task type"))))