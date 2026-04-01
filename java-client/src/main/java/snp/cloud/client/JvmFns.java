package snp.cloud.client;

public final class JvmFns {

    private JvmFns() {
    }

    public static final class MathOps {
        public static int square(int x) {
            return x * x;
        }

        public static boolean isEven(int x) {
            return x % 2 == 0;
        }
    }

    @CloudRequires({MathOps.class})
    public static final class SquareInt implements SerializableFunction<Integer, Integer> {
        @Override
        public Integer apply(Integer x) {
            return MathOps.square(x);
        }
    }

    @CloudRequires({MathOps.class})
    public static final class EvenInt implements SerializablePredicate<Integer> {
        @Override
        public boolean test(Integer x) {
            return MathOps.isEven(x);
        }
    }

    public static final class SumInt implements SerializableBinaryOperator<Integer> {
        @Override
        public Integer apply(Integer a, Integer b) {
            return Integer.sum(a, b);
        }
    }

    public static final class ParseInt implements SerializableFunction<String, Integer> {
        @Override
        public Integer apply(String s) {
            return Integer.valueOf(s);
        }
    }
}
