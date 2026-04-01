package snp.cloud.client;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Демо: локальный {@link Stream} → {@link StreamSource#fromStream(Stream)} →
 * удалённый map/filter/reduce.
 */
public class StreamPassDemo {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://localhost:8080/submit";
        CloudClient client = new CloudClient(url);

        SerializableFunction<Integer, Integer> square = x -> x * x;
        SerializablePredicate<Integer> even = n -> n % 2 == 0;
        SerializableBinaryOperator<Integer> sum = Integer::sum;

        StreamSource fromPipeline = StreamSource.fromStream(
                IntStream.rangeClosed(1, 15).boxed().filter(n -> n % 3 != 0));

        StreamSource fromIntStream = StreamSource.fromIntStream(IntStream.rangeClosed(1, 10));

        System.out.println("fromStream (filtered 1..15): " + client.map(square, fromPipeline));
        System.out.println("intRangeClosed: " + client.map(square, StreamSource.intRangeClosed(1, 5)));
        System.out.println("fromIntStream: " + client.filter(even, fromIntStream));
        System.out.println("reduce: " + client.reduce(sum, 0, StreamSource.intRangeClosed(1, 100)));

        List<String> words = Stream.of("a", "bb", "ccc").collect(Collectors.toList());
        System.out.println("lengths: " + client.map(String::length, StreamSource.fromList(words)));
    }
}
