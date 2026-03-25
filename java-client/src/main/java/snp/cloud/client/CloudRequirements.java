package snp.cloud.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CloudRequirements {
    private CloudRequirements() {
    }

    static List<String> resolveRequiredClasses(Object fnObject) {
        Set<String> required = new LinkedHashSet<>();
        ArrayDeque<Class<?>> queue = new ArrayDeque<>();
        queue.add(fnObject.getClass());

        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            CloudRequires ann = current.getAnnotation(CloudRequires.class);
            if (ann == null) {
                continue;
            }

            for (Class<?> cls : ann.value()) {
                if (required.add(cls.getName())) {
                    queue.add(cls);
                }
            }
        }

        return new ArrayList<>(required);
    }
}
