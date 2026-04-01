(ns dist.client.api
  (:require
   [dist.client.rpc :as rpc]
   [dist.dto :as dto]))

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
  (let [result (rpc/send-task
                {:type :jvm-call
                 :kind kind
                 :fn-ser (dto/serialize-java f)
                 :args-ser (mapv dto/serialize-java args)})]
    (dto/deserialize-java result)))

(defn jvm-mapd [f coll]
  (let [result (rpc/send-task
                {:type :jvm-stream
                 :op :map
                 :fn-ser (dto/serialize-java f)
                 :items-ser (mapv dto/serialize-java coll)})]
    (mapv dto/deserialize-java result)))

(defn jvm-filterd [pred coll]
  (let [result (rpc/send-task
                {:type :jvm-stream
                 :op :filter
                 :fn-ser (dto/serialize-java pred)
                 :items-ser (mapv dto/serialize-java coll)})]
    (mapv dto/deserialize-java result)))

(defn jvm-reduced
  ([op coll]
   (let [result (rpc/send-task
                 {:type :jvm-stream
                  :op :reduce
                  :fn-ser (dto/serialize-java op)
                  :items-ser (mapv dto/serialize-java coll)})]
     (dto/deserialize-java result)))
  ([op init coll]
   (let [result (rpc/send-task
                 {:type :jvm-stream
                  :op :reduce
                  :fn-ser (dto/serialize-java op)
                  :identity-ser (dto/serialize-java init)
                  :items-ser (mapv dto/serialize-java coll)})]
     (dto/deserialize-java result))))
