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
          f (try
              (dto/deserialize-java (:fn-ser task))
              (catch ClassNotFoundException e
                (println "jvm-call: ClassNotFoundException при fn-ser — не используй reify из REPL для удалённого JVM; см. JvmFns / Java-клиент.")
                (throw e)))
          args (mapv dto/deserialize-java (:args-ser task))
          result (dto/invoke-jvm-call (:kind task) f args)]
      (if (:return-serialized? task)
        (dto/serialize-java result)
        result))

    :jvm-reduce-chunk
    (let [_ (dto/assert-required-classes! (:required-classes task))
          f (try
              (dto/deserialize-java (:fn-ser task))
              (catch ClassNotFoundException e
                (println "jvm-reduce-chunk: ClassNotFoundException при fn-ser — см. подсказку для jvm-stream (JvmFns, не reify в REPL).")
                (throw e)))
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

    :jvm-stream
    (let [_ (dto/assert-required-classes! (:required-classes task))
          op (:op task)
          f (try
              (dto/deserialize-java (:fn-ser task))
              (catch ClassNotFoundException e
                (println "jvm-stream: ClassNotFoundException при десериализации fn-ser — часто это reify из Clojure REPL (user$…). Используй snp.cloud.client.JvmFns.* и собери java-client.")
                (throw e)))
          items (if-let [ss (:stream-source-ser task)]
                  (let [src (dto/deserialize-java ss)]
                    (vec (.materialize src)))
                  (mapv dto/deserialize-java (:items-ser task)))]
      (case op
        :map (mapv #(dto/serialize-java (dto/invoke-jvm-call :function f [%])) items)
        :filter (mapv dto/serialize-java (filter #(boolean (dto/invoke-jvm-call :predicate f [%])) items))
        :reduce (throw (Exception. "jvm-stream reduce is handled on server"))))

    (throw (Exception. "Unknown task type"))))