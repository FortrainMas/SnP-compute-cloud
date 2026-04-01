(ns dist.server.endpoints
  (:require
   [dist.dto :as dto]
   [dist.server.scheduler :as scheduler]))

(def result-cache (atom {}))

(def cacheable-types
  #{:call :map :filter :reduce :jvm-stream})

(defn- task-cache-key [task]
  ;; Cache key is derived from full task payload
  (hash (pr-str task)))

(defn- await-promises [submitted]
  (mapv (fn [{:keys [promise]}]
          @promise)
        submitted))

;; -----------------------------
;; SIMPLE TASKS (NO FAN-OUT)
;; -----------------------------

(defn- submit-map-task [task]
  (let [{:keys [promise]} (scheduler/submit-task task)]
    @promise))

(defn- submit-filter-task [task]
  (let [{:keys [promise]} (scheduler/submit-task task)]
    @promise))

;; -----------------------------
;; REDUCE (UNCHANGED LOGIC)
;; -----------------------------

(defn- chunk-size-for [n]
  (max 1 (long (Math/ceil (Math/sqrt (double (max 1 n)))))))

(defn- submit-reduce-task [{:keys [args registry] f :fn}]
  (let [items (vec (doall args))]
    (case (count items)
      0 nil
      1 (first items)
      (let [chunk-size (chunk-size-for (count items))
            chunks (partition-all chunk-size items)
            partial-submitted (mapv (fn [chunk]
                                      (scheduler/submit-task
                                       {:type :reduce
                                        :fn f
                                        :args (vec chunk)
                                        :registry registry}))
                                    chunks)
            partials (await-promises partial-submitted)]
        (if (= 1 (count partials))
          (first partials)
          (let [{:keys [promise]}
                (scheduler/submit-task
                 {:type :reduce
                  :fn f
                  :args partials
                  :registry registry})]
            @promise))))))

;; -----------------------------
;; JVM TASKS (SIMPLIFIED STYLE)
;; -----------------------------

(defn- submit-jvm-map-task [{:keys [fn-ser items-ser required-classes] :as task}]
  (let [{:keys [promise]} (scheduler/submit-task task)]
    @promise))

(defn- submit-jvm-filter-task [{:keys [fn-ser items-ser required-classes] :as task}]
  (let [{:keys [promise]} (scheduler/submit-task task)]
    @promise))

(defn- reduce-chunk-task [fn-ser chunk identity-ser required-classes]
  (scheduler/submit-task
   {:type :jvm-reduce-chunk
    :fn-ser fn-ser
    :required-classes required-classes
    :items-ser (vec chunk)
    :identity-ser identity-ser}))

(defn- submit-jvm-reduce-task [{:keys [fn-ser combine-fn-ser items-ser identity-ser required-classes]}]
  (let [items (vec (doall items-ser))]
    (case (count items)
      0 identity-ser
      1 (if (some? identity-ser)
          (let [{:keys [promise]}
                (reduce-chunk-task fn-ser [(first items)] identity-ser required-classes)]
            @promise)
          (first items))
      (let [chunk-size (chunk-size-for (count items))
            chunks (partition-all chunk-size items)
            partial-submitted (mapv #(reduce-chunk-task fn-ser % identity-ser required-classes) chunks)
            partials (await-promises partial-submitted)]
        (if (= 1 (count partials))
          (first partials)
          (let [{:keys [promise]}
                (reduce-chunk-task (or combine-fn-ser fn-ser) partials nil required-classes)]
            @promise))))))

(defn- submit-jvm-stream-task [{:keys [op] :as task}]
  (case op
    :map (submit-jvm-map-task task)
    :filter (submit-jvm-filter-task task)
    :reduce (submit-jvm-reduce-task task)
    (throw (ex-info "Unknown jvm stream op" {:op op}))))

;; -----------------------------
;; EXECUTION ROUTER
;; -----------------------------

(defn- execute-task-result [task]
  (case (:type task)
    :map (submit-map-task task)
    :filter (submit-filter-task task)
    :reduce (submit-reduce-task task)
    :jvm-stream (submit-jvm-stream-task task)
    :jvm-call (let [{:keys [promise]} (scheduler/submit-task task)]
                @promise)
    (let [{:keys [promise]} (scheduler/submit-task task)]
      @promise)))

;; -----------------------------
;; PUBLIC API
;; -----------------------------

(defn submit-task [task]
  (println "submit task")
  (let [cacheable? (contains? cacheable-types (:type task))
        cache-key (when cacheable? (task-cache-key task))]
    {:promise
     (future
       (if (and cacheable? (contains? @result-cache cache-key))
         (do
           (println "cache hit" (:type task))
           (get @result-cache cache-key))
         (let [result (execute-task-result task)]
           (when cacheable?
             (swap! result-cache assoc cache-key result))
           result)))}))

(defn steal-task []
  (scheduler/steal-task))

(defn complete-task [data]
  (scheduler/complete-task data))

(defn heartbeat [{:keys [id]}]
  (scheduler/heartbeat id)
  :ok)