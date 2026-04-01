(ns dist.server.endpoints-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dist.dto :as dto]
   [dist.server.endpoints :as endpoints]
   [dist.server.scheduler :as scheduler])
  (:import
   [snp.cloud.client StreamSource]))

(defn- resolved-result [task]
  (case (:type task)
    :jvm-call
    (let [f (dto/deserialize-java (:fn-ser task))
          args (mapv dto/deserialize-java (:args-ser task))
          result (dto/invoke-jvm-call (:kind task) f args)]
      (if (:return-serialized? task)
        (dto/serialize-java result)
        result))

    :jvm-reduce-chunk
    (let [f (dto/deserialize-java (:fn-ser task))
          items (mapv dto/deserialize-java (:items-ser task))
          init (dto/deserialize-java (:identity-ser task))
          reduced (if (empty? items)
                    init
                    (if (some? init)
                      (reduce (fn [acc x]
                                (dto/invoke-jvm-call :binary-operator f [acc x]))
                              init
                              items)
                      (reduce (fn [acc x]
                                (dto/invoke-jvm-call :binary-operator f [acc x]))
                              items)))]
      (dto/serialize-java reduced))

    :jvm-stream
    (let [f (dto/deserialize-java (:fn-ser task))
          items (if-let [ss (:stream-source-ser task)]
                  (vec (.materialize (dto/deserialize-java ss)))
                  (mapv dto/deserialize-java (:items-ser task)))]
      (case (:op task)
        :map (mapv #(dto/serialize-java (dto/invoke-jvm-call :function f [%])) items)
        :filter (mapv dto/serialize-java (filter #(boolean (dto/invoke-jvm-call :predicate f [%])) items))
        :reduce (throw (ex-info "reduce via jvm-stream is split into chunks in real scheduler" {:task task}))))

    (throw (ex-info "Unexpected task type in test stub" {:task task}))))

(defn- stub-submit-task [task]
  {:id "test-id"
   :promise (delay (resolved-result task))})

(deftest jvm-stream-map-filter-reduce
  (with-redefs [scheduler/submit-task stub-submit-task]
    (testing "jvm map returns transformed values in order"
      (let [f (reify java.util.function.Function
                java.io.Serializable
                (apply [_ x] (* x x)))
            task {:type :jvm-stream
                  :op :map
                  :fn-ser (dto/serialize-java f)
                  :items-ser (mapv dto/serialize-java [1 2 3 4])}
            result @(-> (endpoints/submit-task task) :promise)]
        (is (= [1 4 9 16] (mapv dto/deserialize-java result)))))

    (testing "jvm filter returns only matching values"
      (let [p (reify java.util.function.Predicate
                java.io.Serializable
                (test [_ x] (even? x)))
            task {:type :jvm-stream
                  :op :filter
                  :fn-ser (dto/serialize-java p)
                  :items-ser (mapv dto/serialize-java [1 2 3 4 5 6])}
            result @(-> (endpoints/submit-task task) :promise)]
        (is (= [2 4 6] (mapv dto/deserialize-java result)))))

    (testing "jvm map with StreamSource (int range)"
      (let [f (reify java.util.function.Function
                java.io.Serializable
                (apply [_ x] (* x x)))
            src (StreamSource/intRangeClosed 1 4)
            task {:type :jvm-stream
                  :op :map
                  :fn-ser (dto/serialize-java f)
                  :stream-source-ser (dto/serialize-java src)
                  :items-ser []}
            result @(-> (endpoints/submit-task task) :promise)]
        (is (= [1 4 9 16] (mapv dto/deserialize-java result)))))

    (testing "jvm reduce supports identity"
      (let [op (reify java.util.function.BinaryOperator
                 java.io.Serializable
                 (apply [_ a b] (+ a b)))
            task {:type :jvm-stream
                  :op :reduce
                  :fn-ser (dto/serialize-java op)
                  :identity-ser (dto/serialize-java 0)
                  :items-ser (mapv dto/serialize-java [1 2 3 4])}
            result @(-> (endpoints/submit-task task) :promise)]
        (is (= 10 (dto/deserialize-java result)))))

    (testing "jvm reduce supports explicit combiner for partial merges"
      (let [acc (reify java.util.function.BinaryOperator
                  java.io.Serializable
                  (apply [_ a b] (* a b)))
            combiner (reify java.util.function.BinaryOperator
                       java.io.Serializable
                       (apply [_ a b] (+ a b)))
            task {:type :jvm-stream
                  :op :reduce
                  :fn-ser (dto/serialize-java acc)
                  :combine-fn-ser (dto/serialize-java combiner)
                  :identity-ser (dto/serialize-java 1)
                  :items-ser (mapv dto/serialize-java [1 2 3 4])}
            result @(-> (endpoints/submit-task task) :promise)]
        ;; chunks [1 2] and [3 4] -> partials 2 and 12, then combined by + -> 14
        (is (= 14 (dto/deserialize-java result)))))))
