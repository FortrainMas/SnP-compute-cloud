package snp.cloud.client;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public interface StreamSource extends Serializable {

    List<Object> materialize();

    static StreamSource intRangeClosed(int fromInclusive, int toInclusive) {
        return new IntRangeClosed(fromInclusive, toInclusive);
    }

    static StreamSource longRangeClosed(long fromInclusive, long toInclusive) {
        return new LongRangeClosed(fromInclusive, toInclusive);
    }

    static StreamSource fromList(List<?> elements) {
        Objects.requireNonNull(elements, "elements");
        return new ListBacked(new ArrayList<>(elements));
    }

    static StreamSource fromStream(Stream<?> stream) {
        Objects.requireNonNull(stream, "stream");
        List<Object> list = stream.collect(Collectors.toList());
        for (Object o : list) {
            if (o != null && !(o instanceof Serializable)) {
                throw new IllegalArgumentException("Non-serializable element: " + o.getClass().getName());
            }
        }
        return new ListBacked(new ArrayList<>(list));
    }

    static StreamSource fromIntStream(IntStream stream) {
        return fromStream(stream.boxed());
    }

    static StreamSource fromLongStream(LongStream stream) {
        return fromStream(stream.boxed());
    }

    final class IntRangeClosed implements StreamSource {
        private static final long serialVersionUID = 1L;

        private final int from;
        private final int toInclusive;

        IntRangeClosed(int fromInclusive, int toInclusive) {
            if (toInclusive < fromInclusive) {
                throw new IllegalArgumentException("toInclusive < fromInclusive");
            }
            this.from = fromInclusive;
            this.toInclusive = toInclusive;
        }

        @Override
        public List<Object> materialize() {
            ArrayList<Object> out = new ArrayList<>(Math.max(0, toInclusive - from + 1));
            for (int i = from; i <= toInclusive; i++) {
                out.add(i);
            }
            return out;
        }
    }

    final class LongRangeClosed implements StreamSource {
        private static final long serialVersionUID = 1L;

        private final long from;
        private final long toInclusive;

        LongRangeClosed(long fromInclusive, long toInclusive) {
            if (toInclusive < fromInclusive) {
                throw new IllegalArgumentException("toInclusive < fromInclusive");
            }
            this.from = fromInclusive;
            this.toInclusive = toInclusive;
        }

        @Override
        public List<Object> materialize() {
            long n = toInclusive - from + 1L;
            if (n > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Range too large to materialize");
            }
            ArrayList<Object> out = new ArrayList<>((int) n);
            for (long i = from; i <= toInclusive; i++) {
                out.add(i);
            }
            return out;
        }
    }

    final class ListBacked implements StreamSource {
        private static final long serialVersionUID = 1L;

        private final ArrayList<Object> elements;

        ListBacked(ArrayList<Object> elements) {
            this.elements = elements;
        }

        @Override
        public List<Object> materialize() {
            return new ArrayList<>(elements);
        }
    }
}
