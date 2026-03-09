(ns dist.client.api
  (:require
   [dist.client.rpc :as rpc]))

(def registry (atom {}))

(defn register-fn [name f]
  (swap! registry assoc name f))

(defmacro defd
  [name args & body]
  `(do
     (swap! registry assoc '~name '(fn ~args ~@body))

     (defn ~name [& params#]
       (rpc/send-task
        {:type :call
         :fn '~name
         :args params#
         :registry @registry}))))

(defmacro mapd [f coll]
  `(rpc/send-task
    {:type :map
     :fn '~f
     :args ~coll
     :registry @registry}))

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
