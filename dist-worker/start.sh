pids=()

echo "Starting workers..."

for i in {1..30}; do
  clj -M -m dist.worker.core > worker_$i.log 2>&1 &
  pids+=($!)
  echo "Started worker $i (PID ${pids[-1]})"
done

echo "All workers started"
echo "Press ENTER to stop..."

read

echo "Stopping workers..."

kill "${pids[@]}"
wait

echo "All workers killed"
