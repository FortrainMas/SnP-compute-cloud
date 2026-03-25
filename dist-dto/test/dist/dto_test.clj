(ns dist.dto-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dist.dto :as dto]))

(deftest serialize-deserialize-roundtrip
  (testing "Serializable objects survive roundtrip"
    (let [value {:a 1 :b [1 2 3]}
          encoded (dto/serialize-java value)
          decoded (dto/deserialize-java encoded)]
      (is (= value decoded)))))

(deftest serialize-non-serializable-throws
  (testing "Non-serializable values are rejected"
    (is (thrown? clojure.lang.ExceptionInfo
                 (dto/serialize-java (Object.))))))

(deftest invoke-jvm-call-works
  (testing "Supported Java functional interfaces are invoked"
    (let [f (reify java.util.function.Function
              (apply [_ x] (* 2 x)))
          p (reify java.util.function.Predicate
              (test [_ x] (even? x)))
          bi (reify java.util.function.BiFunction
               (apply [_ a b] (+ a b)))
          op (reify java.util.function.BinaryOperator
               (apply [_ a b] (* a b)))]
      (is (= 6 (dto/invoke-jvm-call :function f [3])))
      (is (true? (dto/invoke-jvm-call :predicate p [4])))
      (is (= 7 (dto/invoke-jvm-call :bi-function bi [3 4])))
      (is (= 12 (dto/invoke-jvm-call :binary-operator op [3 4]))))))
