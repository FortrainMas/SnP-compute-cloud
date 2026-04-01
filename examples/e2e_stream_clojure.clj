;; E2E из Clojure: server + worker(s), заранее: mvn compile в java-client
;;
;; НЕ используй (reify Function ...) из REPL для jvm-mapd — класс типа user$reify__*
;; есть только у клиента; на воркере десериализация даст ClassNotFoundException.
;; Берём готовые классы из snp.cloud.client.JvmFns (они в target/classes воркера).

(require '[dist.client.api :as cloud])
(import '[snp.cloud.client StreamSource JvmFns$SquareInt JvmFns$EvenInt JvmFns$SumInt])

(println :map (cloud/jvm-mapd (JvmFns$SquareInt.) (StreamSource/intRangeClosed 1 8)))
(println :filter (cloud/jvm-filterd (JvmFns$EvenInt.) (StreamSource/intRangeClosed 1 20)))
(println :reduce (cloud/jvm-reduced (JvmFns$SumInt.) 0 (StreamSource/intRangeClosed 1 100)))
