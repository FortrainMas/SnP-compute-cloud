# SnP-compute-cloud
> Хайповая платформа для тех, кто двигается на модных движениях

### О проекте
SnP-compute-cloud - это платформа для распределенных вычислений, на выделенных машинах. Необходим один сервер, к нему подключаются worker-ы и client-ы. Одни выполняют задачу, другие ставят их. 

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
Клиент предполагается в виде библиотеке к другому clojure-коду, поэтому у него точки входа main. Для тестирования его можно запустить в REPL
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