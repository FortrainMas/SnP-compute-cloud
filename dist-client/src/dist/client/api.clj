(ns dist.client.api
  (:require
   [clojure.string :as str]
   [dist.client.rpc :as rpc]
   [dist.dto :as dto])
  (:import
   [snp.cloud.client StreamSource]))

(defn- assert-jvm-remote-safe!
  "Сериализованный объект должен быть классом, который есть на воркере (тот же classpath).
  Классы из REPL (reify, user$…) на воркере не найдутся."
  [x label]
  (when x
    (let [n (.getName (class x))]
      (when (or (str/starts-with? n "user$")
                (str/includes? n "$reify"))
        (throw (ex-info
                (str label ": нельзя отправить на воркер класс " n " — это обычно reify/аноним из REPL. "
                     "Используй скомпилированные классы snp.cloud.client.JvmFns (см. examples/e2e_stream_clojure.clj), "
                     "Java CloudClient с лямбдами из того же jar, или Clojure mapd/defde без jvm-mapd.")
                {:class n}))))))

(def registry (atom {}))

(defn register-fn [name f]
  (swap! registry assoc name f))

(defmacro defd
  [name args & body]
  `(do
     (swap! registry assoc '~name (fn ~args ~@body))

     (defn ~name ~args
       ~@body)))

(defmacro defde
  [name args & body]
  `(do
     (swap! registry assoc '~name '(fn ~args ~@body))

     (defn ~name [& params#]
       (rpc/send-task
        {:type :call
         :fn '~name
         :args params#
         :registry @registry}))))

(defn split-into [n coll]
  (let [size (int (Math/ceil (/ (count coll) (double n))))]
    (partition-all size coll)))

(defmacro mapd [f coll]
  `(let [chunks# (split-into 30 ~coll)
         futures# (map (fn [chunk#]
                         (future
                           (rpc/send-task
                             {:type :map
                              :fn '~f
                              :args chunk#
                              :registry @registry})))
                       chunks#)]

     (->> futures#
          (map deref)
          (apply concat))))

(defmacro filterd [f coll]
  `(rpc/send-task
    {:type :filter
     :fn '~f
     :args ~coll
     :registry @registry}))

(defmacro reduced [f coll]
  `(rpc/send-task
    {:type :reduce
     :fn '~f
     :args ~coll
     :registry @registry}))

(defn jvm-call
  [kind f & args]
  (assert-jvm-remote-safe! f "jvm-call fn")
  (doseq [a args]
    (assert-jvm-remote-safe! a "jvm-call arg"))
  (let [result (rpc/send-task
                {:type :jvm-call
                 :kind kind
                 :fn-ser (dto/serialize-java f)
                 :args-ser (mapv dto/serialize-java args)})]
    (dto/deserialize-java result)))

(defn jvm-mapd [f coll-or-source]
  (assert-jvm-remote-safe! f "jvm-mapd")
  (let [result (if (instance? StreamSource coll-or-source)
                 (rpc/send-task
                  {:type :jvm-stream
                   :op :map
                   :fn-ser (dto/serialize-java f)
                   :stream-source-ser (dto/serialize-java coll-or-source)
                   :items-ser []})
                 (rpc/send-task
                  {:type :jvm-stream
                   :op :map
                   :fn-ser (dto/serialize-java f)
                   :items-ser (mapv dto/serialize-java coll-or-source)}))]
    (mapv dto/deserialize-java result)))

(defn jvm-filterd [pred coll-or-source]
  (assert-jvm-remote-safe! pred "jvm-filterd")
  (let [result (if (instance? StreamSource coll-or-source)
                 (rpc/send-task
                  {:type :jvm-stream
                   :op :filter
                   :fn-ser (dto/serialize-java pred)
                   :stream-source-ser (dto/serialize-java coll-or-source)
                   :items-ser []})
                 (rpc/send-task
                  {:type :jvm-stream
                   :op :filter
                   :fn-ser (dto/serialize-java pred)
                   :items-ser (mapv dto/serialize-java coll-or-source)}))]
    (mapv dto/deserialize-java result)))

(defn jvm-reduced
  ([op coll-or-source]
   (assert-jvm-remote-safe! op "jvm-reduced")
   (let [result (if (instance? StreamSource coll-or-source)
                  (rpc/send-task
                   {:type :jvm-stream
                    :op :reduce
                    :fn-ser (dto/serialize-java op)
                    :stream-source-ser (dto/serialize-java coll-or-source)
                    :items-ser []})
                  (rpc/send-task
                   {:type :jvm-stream
                    :op :reduce
                    :fn-ser (dto/serialize-java op)
                    :items-ser (mapv dto/serialize-java coll-or-source)}))]
     (dto/deserialize-java result)))
  ([op init coll-or-source]
   (assert-jvm-remote-safe! op "jvm-reduced")
   (let [result (if (instance? StreamSource coll-or-source)
                  (rpc/send-task
                   {:type :jvm-stream
                    :op :reduce
                    :fn-ser (dto/serialize-java op)
                    :identity-ser (dto/serialize-java init)
                    :stream-source-ser (dto/serialize-java coll-or-source)
                    :items-ser []})
                  (rpc/send-task
                   {:type :jvm-stream
                    :op :reduce
                    :fn-ser (dto/serialize-java op)
                    :identity-ser (dto/serialize-java init)
                    :items-ser (mapv dto/serialize-java coll-or-source)}))]
     (dto/deserialize-java result))))
