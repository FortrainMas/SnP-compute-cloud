package snp.cloud.client;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CloudClient {
    private static final IFn PR_STR = Clojure.var("clojure.core", "pr-str");
    private static final IFn EDN_READ_STRING = Clojure.var("clojure.edn", "read-string");

    private static final Keyword K_TYPE = Keyword.intern("type");
    private static final Keyword K_OP = Keyword.intern("op");
    private static final Keyword K_FN_SER = Keyword.intern("fn-ser");
    private static final Keyword K_ITEMS_SER = Keyword.intern("items-ser");
    private static final Keyword K_IDENTITY_SER = Keyword.intern("identity-ser");
    private static final Keyword K_REQUIRED_CLASSES = Keyword.intern("required-classes");

    private static final Keyword V_JVM_STREAM = Keyword.intern("jvm-stream");
    private static final Keyword V_MAP = Keyword.intern("map");
    private static final Keyword V_FILTER = Keyword.intern("filter");
    private static final Keyword V_REDUCE = Keyword.intern("reduce");

    private final HttpClient httpClient;
    private final URI submitUri;

    public CloudClient(String serverSubmitUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.submitUri = URI.create(serverSubmitUrl);
    }

    public <T, R> List<R> map(SerializableFunction<T, R> function, List<T> items) {
        PersistentVector requiredClasses = PersistentVector.create(CloudRequirements.resolveRequiredClasses(function));
        IPersistentMap task = PersistentHashMap.create(
                K_TYPE, V_JVM_STREAM,
                K_OP, V_MAP,
                K_FN_SER, SerializationUtils.serializeToBase64(function),
                K_REQUIRED_CLASSES, requiredClasses,
                K_ITEMS_SER, serializeList(items)
        );
        Object result = sendTask(task);
        return deserializeList(castToList(result));
    }

    public <T> List<T> filter(SerializablePredicate<T> predicate, List<T> items) {
        PersistentVector requiredClasses = PersistentVector.create(CloudRequirements.resolveRequiredClasses(predicate));
        IPersistentMap task = PersistentHashMap.create(
                K_TYPE, V_JVM_STREAM,
                K_OP, V_FILTER,
                K_FN_SER, SerializationUtils.serializeToBase64(predicate),
                K_REQUIRED_CLASSES, requiredClasses,
                K_ITEMS_SER, serializeList(items)
        );
        Object result = sendTask(task);
        return deserializeList(castToList(result));
    }

    public <T> T reduce(SerializableBinaryOperator<T> operator, List<T> items) {
        PersistentVector requiredClasses = PersistentVector.create(CloudRequirements.resolveRequiredClasses(operator));
        IPersistentMap task = PersistentHashMap.create(
                K_TYPE, V_JVM_STREAM,
                K_OP, V_REDUCE,
                K_FN_SER, SerializationUtils.serializeToBase64(operator),
                K_REQUIRED_CLASSES, requiredClasses,
                K_ITEMS_SER, serializeList(items)
        );
        Object result = sendTask(task);
        return cast(SerializationUtils.deserializeFromBase64((String) result));
    }

    public <T> T reduce(SerializableBinaryOperator<T> operator, T identity, List<T> items) {
        PersistentVector requiredClasses = PersistentVector.create(CloudRequirements.resolveRequiredClasses(operator));
        IPersistentMap task = PersistentHashMap.create(
                K_TYPE, V_JVM_STREAM,
                K_OP, V_REDUCE,
                K_FN_SER, SerializationUtils.serializeToBase64(operator),
                K_REQUIRED_CLASSES, requiredClasses,
                K_IDENTITY_SER, SerializationUtils.serializeToBase64(identity),
                K_ITEMS_SER, serializeList(items)
        );
        Object result = sendTask(task);
        return cast(SerializationUtils.deserializeFromBase64((String) result));
    }

    private Object sendTask(IPersistentMap task) {
        String body = (String) PR_STR.invoke(task);
        HttpRequest request = HttpRequest.newBuilder(submitUri)
                .header("Content-Type", "application/edn")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(2))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Server returned status " + response.statusCode() + ": " + response.body());
            }
            return EDN_READ_STRING.invoke(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting cloud response", e);
        }
    }

    private static PersistentVector serializeList(List<?> values) {
        List<String> encoded = new ArrayList<>(values.size());
        for (Object value : values) {
            encoded.add(SerializationUtils.serializeToBase64(value));
        }
        return PersistentVector.create(encoded);
    }

    private static <T> List<T> deserializeList(List<?> serializedValues) {
        List<T> decoded = new ArrayList<>(serializedValues.size());
        for (Object encoded : serializedValues) {
            decoded.add(cast(SerializationUtils.deserializeFromBase64((String) encoded)));
        }
        return decoded;
    }

    private static List<?> castToList(Object result) {
        if (result instanceof List<?>) {
            return (List<?>) result;
        }
        throw new IllegalStateException("Expected list response, got: " + (result == null ? "null" : result.getClass().getName()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
