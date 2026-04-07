package talan.pfe.rulengine.util;

import java.util.Map;

public final class InputFieldPath {

    private InputFieldPath() {
    }

    @SuppressWarnings("unchecked")
    public static Object resolve(Map<String, Object> root, String fieldPath) {
        if (root == null || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        String[] parts = fieldPath.split("\\.");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(part);
        }
        return cur;
    }
}
