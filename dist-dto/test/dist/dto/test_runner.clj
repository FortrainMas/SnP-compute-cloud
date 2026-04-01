(ns dist.dto.test-runner
  (:require
   [clojure.test :as t]
   [dist.dto-test]))

(defn -main []
  (let [result (t/run-tests 'dist.dto-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
