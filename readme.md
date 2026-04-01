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
;; Лучше (:as cloud) вместо (:refer :all): макрос reduced из api перекрывает clojure.core/reduced.
(require '[dist.client.api :as cloud :refer [defd defde]])
(cloud/defd square [x] (* x x))
(square 5)
;; 25
(cloud/defd discriminant [a b c]
  (- (square b) (* 4 a c)))
(discriminant 3 4 2)
;; -8
```

### Smoke-тест распределенных map/filter/reduce

Запусти `server` и минимум 2 `worker` процесса в отдельных терминалах, затем в REPL клиента:

```clojure
(require '[dist.client.api :as cloud :refer [defde mapd filterd reduced]])

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

Для `:jvm-stream` `reduce` теперь поддерживается отдельный оператор объединения частичных результатов:

- `:fn-ser` — accumulator для редукции внутри чанка;
- `:combine-fn-ser` (опционально) — combiner для слияния partial-результатов между задачами.

Пример в Clojure (удалённое JVM, воркер десериализует код **того же класса**, что есть в `java-client/target/classes`):

```clojure
(require '[dist.client.api :as cloud])
(import '[snp.cloud.client JvmFns$SquareInt JvmFns$EvenInt JvmFns$SumInt])

(cloud/jvm-mapd (JvmFns$SquareInt.) (range 1 6))
;; => [1 4 9 16 25]

(cloud/jvm-filterd (JvmFns$EvenInt.) (range 1 11))
;; => [2 4 6 8 10]

(cloud/jvm-reduced (JvmFns$SumInt.) 0 (range 1 11))
;; => 55
```

**Почему нельзя `(reify Function ...)` в REPL для `jvm-mapd`:** сериализация запишет имя класса вроде `user$reify__2831`, этого класса нет на воркере → `ClassNotFoundException`. Для REPL используй скомпилированные типы (`JvmFns`) или чисто Clojure `mapd`/`defde`.

Ограничения:

- передаваемые объекты и функции должны быть `java.io.Serializable`;
- для Java-лямбд используй serializable functional interfaces **из того же JAR/classes, что подключены к воркеру**;
- `jvm-mapd`/`jvm-filterd`/`jvm-reduced` работают через задачу типа `:jvm-stream`.

Передача «как Stream» (без отправки самого `java.util.stream.Stream`):

- В Java используй `StreamSource`: диапазон (`intRangeClosed`), материализация локального `Stream` (`fromStream`), список (`fromList`). В задаче уходит поле `:stream-source-ser`.
- В Clojure передай вторым аргументом `(StreamSource/intRangeClosed 1 10)` и т.п. (нужен собранный `java-client/target/classes` в classpath клиента).
- См. `snp.cloud.client.StreamPassDemo`.

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

Демонстрация Stream API (локально) + лямбды и ссылки на методы на `Serializable*`-интерфейсах (удалённо):

```bash
mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.StreamAndLambdaDemo
# опционально: другой URL сервера
# mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.StreamAndLambdaDemo -Dexec.args="http://host:8080/submit"
```

Скрипт для REPL Clojure (те же идеи): `examples/stream_lambda_demo.clj`.

`Main` выполняет распределенные операции:

- `map` через `CloudClient.map(...)`
- `filter` через `CloudClient.filter(...)`
- `reduce` через `CloudClient.reduce(...)`

Для Java также доступна перегрузка в стиле параллельного reduce:

- `reduce(accumulator, identity, items)`
- `reduce(accumulator, combiner, identity, items)`

Важно для Java-лямбд:

- перед запуском `worker` нужно собрать `java-client`, чтобы появились классы в `java-client/target/classes`;
- `worker` должен запускаться с classpath, содержащим эти классы (в проекте это уже добавлено в `dist-worker/deps.edn`).
- можно явно задать зависимости через аннотацию `@CloudRequires({MyHelper.class, ...})` на serializable-функции; клиент передаст `required-classes`, а воркер проверит наличие классов до выполнения.

### E2E: проверить, что `StreamSource` + лямбды реально ходят по сети

Нужны **1× server**, **минимум 1× worker** (лучше 2), заранее собранный **`java-client`** (`mvn compile`).

**1. Терминалы инфраструктуры**

```bash
# терминал A
cd dist-server && clj -M -m dist.server.core

# терминал B (и при желании C)
cd dist-worker && clj -M -m dist.worker.core
```

**2. Java: Stream API → `StreamSource` → удалённые map/filter/reduce**

```bash
cd java-client
mvn -q -DskipTests -Dmaven.repo.local=.m2 compile
mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.StreamPassDemo
```

Ожидаешь строки с результатами (квадраты, фильтр чётных, сумма, длины строк). Здесь же проверяются **лямбды** (`x -> x*x`) и **ссылки на методы** (`Integer::sum`, `String::length`).
