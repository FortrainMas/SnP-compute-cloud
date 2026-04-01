(ns dist.client.rpc
  (:require
   [clj-http.client :as http]
   [clojure.edn :as edn]))

(def server-url "http://localhost:8080/submit")

(defn send-task [task]
  (let [resp (http/post server-url
                        {:body (pr-str task)
                         :headers {"Content-Type" "application/edn"}})]
    (edn/read-string (:body resp))))

(defn send-task-async [task]
  (future
    (let [resp (http/post server-url
                          {:body (pr-str task)
                           :headers {"Content-Type" "application/edn"}})]
      (edn/read-string (:body resp)))))
