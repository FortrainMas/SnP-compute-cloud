;; Примеры dist-client. Для jvm-mapd / jvm-filterd / jvm-reduced на воркер уходят
;; скомпилированные классы — см. snp.cloud.client.JvmFns (не reify в REPL).
(require '[dist.client.api :as cloud])
(import '[java.util.stream Collectors IntStream])
(import '[snp.cloud.client StreamSource JvmFns$SquareInt JvmFns$EvenInt JvmFns$SumInt JvmFns$ParseInt])

(def nums (vec (range 1 13)))
(def words (->> ["alpha" "beta" "gamma" "delta"]
                (filter #(> (count %) 4))
                (map #(.toUpperCase ^String %))
                vec))

(cloud/jvm-mapd (JvmFns$SquareInt.) nums)
(cloud/jvm-filterd (JvmFns$EvenInt.) nums)
(cloud/jvm-reduced (JvmFns$SumInt.) 0 nums)

(cloud/jvm-mapd (JvmFns$SquareInt.) (StreamSource/intRangeClosed 1 10))
(cloud/jvm-reduced (JvmFns$SumInt.) 0 (StreamSource/intRangeClosed 1 100))

(cloud/jvm-mapd (JvmFns$ParseInt.) ["1" "2" "3"])

(def from-stream
  (-> (IntStream/rangeClosed 1 6)
      (.boxed)
      (.collect (Collectors/toList))))
(cloud/jvm-mapd (JvmFns$SquareInt.) (vec from-stream))
