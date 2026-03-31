package snp.cloud.client;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    @CloudRequires({MathOps.class})
    static final class SquareFn implements SerializableFunction<Integer, Integer> {
        @Override
        public Integer apply(Integer x) {
            return MathOps.square(x);
        }
    }

    @CloudRequires({MathOps.class})
    static final class EvenFn implements SerializablePredicate<Integer> {
        @Override
        public boolean test(Integer x) {
            return MathOps.isEven(x);
        }
    }

    static final class SumOp implements SerializableBinaryOperator<Integer> {
        @Override
        public Integer apply(Integer a, Integer b) {
            return Integer.sum(a, b);
        }
    }

    static final class MathOps {
        static int square(int x) {
            return x * x;
        }

        static boolean isEven(int x) {
            return x % 2 == 0;
        }
    }

    public static void main(String[] args) {
        CloudClient client = new CloudClient("http://localhost:8080/submit");
        List<Integer> items = IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList());

        List<Integer> squares = client.map(new SquareFn(), items);
        List<Integer> evens = client.filter(new EvenFn(), items);
        Integer sum = client.reduce(new SumOp(), 0, items);
        Integer sumWithCombiner = client.reduce(new SumOp(), new SumOp(), 0, items);

        System.out.println("map result size: " + squares.size());
        System.out.println("filter result: " + evens);
        System.out.println("reduce result: " + sum);
        System.out.println("reduce with explicit combiner result: " + sumWithCombiner);
    }
}
