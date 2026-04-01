package snp.cloud.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Демонстрация: Stream API строит данные локально; в кластер уходят
 * {@link List} + сериализуемые функции ({@link SerializableFunction} и т.д.).
 * <p>
 * Важно: сам объект {@link java.util.stream.Stream} по сети не передаётся
 * (не {@link java.io.Serializable}). Передаются коллекции и замыкания.
 * Лямбды должны быть на интерфейсах, которые расширяют {@link java.io.Serializable}
 * (иначе {@link SerializationUtils} отклонит значение).
 */
public class StreamAndLambdaDemo {

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://localhost:8080/submit";
        CloudClient client = new CloudClient(url);

        // --- 1) Данные через Stream API (локально) ---
        List<Integer> nums = IntStream.rangeClosed(1, 12)
                .boxed()
                .collect(Collectors.toList());

        List<String> words = Stream.of("alpha", "beta", "gamma", "delta")
                .filter(w -> w.length() > 4)
                .map(String::toUpperCase)
                .collect(Collectors.toList());

        // --- 2) Лямбды на Serializable*-интерфейсах (удалённое выполнение) ---
        SerializableFunction<Integer, Integer> lambdaSquare = x -> x * x;
        SerializablePredicate<Integer> lambdaEven = n -> n % 2 == 0;
        SerializableBinaryOperator<Integer> lambdaSum = (a, b) -> a + b;

        // --- 3) Ссылки на методы (тоже Serializable при наших интерфейсах) ---
        SerializableFunction<String, Integer> methodRefLen = String::length;
        SerializableBinaryOperator<Integer> methodRefSum = Integer::sum;

        // --- 4) Локальная самопроверка сериализации (до HTTP) ---
        verifySerializable("lambda map", lambdaSquare);
        verifySerializable("lambda filter", lambdaEven);
        verifySerializable("lambda reduce", lambdaSum);
        verifySerializable("method ref map", methodRefLen);
        verifySerializable("method ref reduce", methodRefSum);

        // --- 5) Удалённые map / filter / reduce ---
        List<Integer> squares = client.map(lambdaSquare, nums);
        List<Integer> evens = client.filter(lambdaEven, nums);
        Integer sum = client.reduce(lambdaSum, 0, nums);
        Integer sumMr = client.reduce(methodRefSum, 0, nums);

        List<Integer> lengths = client.map(methodRefLen, words);

        System.out.println("nums: " + nums);
        System.out.println("words (local stream pipeline): " + words);
        System.out.println("remote map (lambda x*x): " + squares);
        System.out.println("remote filter (lambda even): " + evens);
        System.out.println("remote reduce (lambda +): " + sum);
        System.out.println("remote reduce (Integer::sum): " + sumMr);
        System.out.println("remote map (String::length): " + lengths);

        // --- 6) Анонимный класс (всегда ок для демонстрации «не лямбда») ---
        SerializableFunction<Integer, Integer> anon = new SerializableFunction<Integer, Integer>() {
            @Override
            public Integer apply(Integer x) {
                return x + 100;
            }
        };
        verifySerializable("anonymous class", anon);
        System.out.println("remote map (anonymous +100): " + client.map(anon, Arrays.asList(1, 2, 3)));
    }

    private static void verifySerializable(String label, Object fn) {
        try {
            String ser = SerializationUtils.serializeToBase64(fn);
            Object back = SerializationUtils.deserializeFromBase64(ser);
            if (back == null) {
                throw new IllegalStateException("deserialized null");
            }
            System.out.println("[ok] serialize roundtrip: " + label);
        } catch (Exception e) {
            System.err.println("[fail] " + label + ": " + e.getMessage());
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
