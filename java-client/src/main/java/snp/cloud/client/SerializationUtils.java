package snp.cloud.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

public final class SerializationUtils {
    private SerializationUtils() {
    }

    public static String serializeToBase64(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("Value must implement Serializable: " + value.getClass().getName());
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(value);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    public static Object deserializeFromBase64(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }
}
