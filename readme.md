# SnP-compute-cloud

> Хайповая платформа для тех, кто двигается на модных движениях

### О проекте

SnP-compute-cloud - это платформа для распределенных вычислений, на выделенных машинах. Необходим один сервер, к нему подключаются worker-ы и client-ы. Одни выполняют задачи, другие ставят их.

### Как запускать

#### Server

```bash
cd dist-server
clj -M -m dist.server.core
```

#### Worker

```bash
cd dist-worker
clj -M -m dist.worker.core
```

#### Client

Клиент предполагается в виде библиотеке к другому clojure-коду, поэтому у него нет точки входа main. Для тестирования его можно запустить в REPL

```bash
cd dist-client
clj
```

В REPL

```clojure
(require '[dist.client.api :refer :all])
(defd square [x] (* x x))
(square 5)
;; 25
(defd discriminant [a b c]
  (- (square b) (* 4 a c)))
(discriminant 3 4 2)
;; -8
```

### Smoke-тест распределенных map/filter/reduce

Запусти `server` и минимум 2 `worker` процесса в отдельных терминалах, затем в REPL клиента:

```clojure
(require '[dist.client.api :refer :all])

;; Регистрируем функции в реестре клиента
(defde slow-square [x]
  (Thread/sleep 100)
  (* x x))

(defde evenn? [x]
  (Thread/sleep 50)
  (zero? (mod x 2)))

(defde sum2 [a b]
  (Thread/sleep 20)
  (+ a b))

(def items (range 1 21))

;; map
(time (def map-res (mapd slow-square items)))
;; Ожидаем: [1 4 9 ... 400]

;; filter
(time (def filter-res (filterd evenn? items)))
;; Ожидаем: [2 4 6 8 10 12 14 16 18 20]

;; reduce
(time (def reduce-res (reduced sum2 items)))
;; Ожидаем: 210
```

### Кэширование результатов (Clojure + Java)

На сервере добавлен кэш результатов задач для типов:

- `:call`
- `:map`
- `:filter`
- `:reduce`
- `:jvm-stream`

Ключ кэша считается из полного payload задачи (`pr-str` задачи), то есть одинаковые функция/код + аргументы возвращают результат из кэша.  
Это работает автоматически и для Clojure-клиента, и для Java `CloudClient`, потому что оба используют `/submit`.

### Поддержка JVM-объектов, лямбд и Stream-операций

Добавлена сериализация JVM-объектов и удалённое выполнение Java-совместимых операций:

- `jvm-call` — удалённый вызов `Function` / `Predicate` / `BiFunction` / `BinaryOperator`
- `jvm-mapd` — распределённый `map`
- `jvm-filterd` — распределённый `filter`
- `jvm-reduced` — распределённый `reduce` (с optional `init`)

Пример в Clojure (для JVM-функциональных интерфейсов):

```clojure
(require '[dist.client.api :refer :all])
(import '[java.util.function Function Predicate BinaryOperator])

(def square
  (reify Function
    (apply [_ x] (* x x))
    java.io.Serializable))

(def even?
  (reify Predicate
    (test [_ x] (zero? (mod x 2)))
    java.io.Serializable))

(def sum-op
  (reify BinaryOperator
    (apply [_ a b] (+ a b))
    java.io.Serializable))

(jvm-mapd square (range 1 6))
;; => [1 4 9 16 25]

(jvm-filterd even? (range 1 11))
;; => [2 4 6 8 10]

(jvm-reduced sum-op 0 (range 1 11))
;; => 55
```

Ограничения:

- передаваемые объекты и функции должны быть `java.io.Serializable`;
- для Java-лямбд используй serializable functional interfaces;
- `jvm-mapd`/`jvm-filterd`/`jvm-reduced` работают через задачу типа `:jvm-stream`.

### Java SDK (`java-client`)

Добавлен модуль `java-client` с клиентом `CloudClient` и serializable-интерфейсами:

- `SerializableFunction`
- `SerializablePredicate`
- `SerializableBinaryOperator`

Пример запуска:

```bash
cd java-client
mvn -q -DskipTests -Dmaven.repo.local=.m2 compile
mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.Main
```

`Main` выполняет распределенные операции:

- `map` через `CloudClient.map(...)`
- `filter` через `CloudClient.filter(...)`
- `reduce` через `CloudClient.reduce(...)`

Важно для Java-лямбд:

- перед запуском `worker` нужно собрать `java-client`, чтобы появились классы в `java-client/target/classes`;
- `worker` должен запускаться с classpath, содержащим эти классы (в проекте это уже добавлено в `dist-worker/deps.edn`).
- можно явно задать зависимости через аннотацию `@CloudRequires({MyHelper.class, ...})` на serializable-функции; клиент передаст `required-classes`, а воркер проверит наличие классов до выполнения.
