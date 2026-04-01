#!/usr/bin/env bash
# Сборка java-client и подсказка по ручному E2E (StreamSource + лямбды).
# Сервер и воркеры нужно запустить отдельно.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/java-client"
mvn -q -DskipTests -Dmaven.repo.local=.m2 compile
echo ""
echo "=== Сборка java-client: OK ==="
echo ""
echo "Дальше в отдельных терминалах:"
echo "  cd $ROOT/dist-server && clj -M -m dist.server.core"
echo "  cd $ROOT/dist-worker && clj -M -m dist.worker.core"
echo ""
echo "Потом Java (StreamSource + лямбды):"
echo "  cd $ROOT/java-client && mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.StreamPassDemo"
echo ""
echo "Или только лямбды/методы:"
echo "  cd $ROOT/java-client && mvn -q -Dmaven.repo.local=.m2 exec:java -Dexec.mainClass=snp.cloud.client.StreamAndLambdaDemo"
echo ""
echo "Clojure REPL:"
echo "  cd $ROOT/dist-client && clj"
echo "  затем вставь пример из examples/e2e_stream_clojure.clj"
echo ""
