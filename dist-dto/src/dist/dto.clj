(ns dist.dto)

(defrecord Task
  [id
   type
   fn
   args])

(defrecord TaskResult
  [task-id
   status
   result
   error])
