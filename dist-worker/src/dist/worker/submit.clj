(ns dist.worker.submit
  (:require
   [clj-http.client :as http]
   [clojure.edn :as edn]))

(def server-url "http://localhost:8080/result")

(defn submit-result [id result]
    (println "submitting task")
  (let [resp (http/post server-url
                        {:body (pr-str {:id id :result result})
                         :headers {"Content-Type" "application/edn"}})]
    (edn/read-string (:body resp))))
