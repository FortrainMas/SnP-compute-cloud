(ns dist.worker.scheduler 
    (:require
        [clj-http.client :as http]
        [clojure.edn :as edn]))

(def threads 1)

(def queues
  (vec (repeatedly threads #(java.util.concurrent.ConcurrentLinkedDeque.))))

(defn steal-task [id]

  (let [victim (rand-int threads)
        q (queues victim)]

    (.pollFirst q)))

(def server-host "http://127.0.0.1:8080")

(defn steal-from-cluster []
  ;; воркер пытается украсть задачу у сервера
  (try
    (let [resp (http/get (str server-host "/steal"))]
      (edn/read-string (:body resp)))
    (catch Exception _ nil)))
