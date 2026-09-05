package ca.foodinventory.api;

final class Json {

    private Json() {
    }

    static String object(String... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key/value pairs.");
        }

        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < fields.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }

            json.append('"')
                    .append(escape(fields[i]))
                    .append("\":\"")
                    .append(escape(fields[i + 1]))
                    .append('"');
        }

        return json.append('}').toString();
    }

    static String nullableString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escape(value) + "\"";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u%04x".formatted((int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
