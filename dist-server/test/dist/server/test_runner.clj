(ns dist.server.test-runner
  (:require
   [clojure.test :as t]
   [dist.server.endpoints-test]
   [dist.server.scheduler :as scheduler]))

(defn -main []
  (let [result (t/run-tests 'dist.server.endpoints-test)]
    (.shutdownNow scheduler/scheduler)
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))
    (System/exit 0)))
