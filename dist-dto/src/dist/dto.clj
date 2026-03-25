(ns dist.dto)

(import
 [java.io ByteArrayInputStream ByteArrayOutputStream
  ObjectInputStream ObjectOutputStream Serializable]
 [java.util Base64]
 [java.util.function BiFunction BinaryOperator Function Predicate])

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

(defn serialize-java [value]
  (when-not (or (nil? value) (instance? Serializable value))
    (throw (ex-info "Value is not java.io.Serializable"
                    {:class (class value)})))
  (if (nil? value)
    nil
    (let [baos (ByteArrayOutputStream.)
          _ (with-open [oos (ObjectOutputStream. baos)]
              (.writeObject oos value))]
      (.encodeToString (Base64/getEncoder) (.toByteArray baos)))))

(defn deserialize-java [encoded]
  (if (nil? encoded)
    nil
    (let [bytes (.decode (Base64/getDecoder) encoded)]
      (with-open [ois (ObjectInputStream. (ByteArrayInputStream. bytes))]
        (.readObject ois)))))

(defn invoke-jvm-call [kind f args]
  (case kind
    :function (.apply ^Function f (first args))
    :predicate (.test ^Predicate f (first args))
    :bi-function (.apply ^BiFunction f (first args) (second args))
    :binary-operator (.apply ^BinaryOperator f (first args) (second args))
    (throw (ex-info "Unsupported JVM call kind" {:kind kind}))))

(defn assert-required-classes! [required-classes]
  (doseq [class-name required-classes]
    (try
      (Class/forName class-name)
      (catch ClassNotFoundException _
        (throw (ex-info "Required class is missing on worker classpath"
                        {:missing-class class-name})))))) 
