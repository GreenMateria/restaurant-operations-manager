package ca.foodinventory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiJsonParser {

    public Map<String, Object> parseObject(String json) {
        return new Cursor(json).readObject();
    }

    private static class Cursor {

        private final String text;
        private int index;

        Cursor(String text) {
            this.text = text == null ? "" : text;
        }

        private Map<String, Object> readObject() {
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();

            if (peek('}')) {
                index++;
                return object;
            }

            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                object.put(key, readValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }

                expect('}');
                return object;
            }
        }

        private List<Object> readArray() {
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            expect('[');
            skipWhitespace();

            if (peek(']')) {
                index++;
                return values;
            }

            while (true) {
                values.add(readValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }

                expect(']');
                return values;
            }
        }

        private Object readValue() {
            skipWhitespace();
            if (peek('"')) {
                return readString();
            }
            if (match("null")) {
                return null;
            }
            if (match("true")) {
                return true;
            }
            if (match("false")) {
                return false;
            }
            if (peek('{')) {
                return readObject();
            }
            if (peek('[')) {
                return readArray();
            }
            return readNumber();
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();

            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return value.toString();
                }

                if (ch != '\\') {
                    value.append(ch);
                    continue;
                }

                if (index >= text.length()) {
                    throw invalidJson("Unterminated escape sequence.");
                }

                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw invalidJson("Unsupported escape sequence.");
                }
            }

            throw invalidJson("Unterminated string.");
        }

        private char readUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw invalidJson("Invalid unicode escape.");
            }

            String hex = text.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw invalidJson("Invalid unicode escape.");
            }
        }

        private Number readNumber() {
            int start = index;
            while (index < text.length()) {
                char ch = text.charAt(index);
                if ((ch >= '0' && ch <= '9')
                        || ch == '-'
                        || ch == '+'
                        || ch == '.'
                        || ch == 'e'
                        || ch == 'E') {
                    index++;
                    continue;
                }
                break;
            }

            if (start == index) {
                throw invalidJson("Expected JSON value.");
            }

            String number = text.substring(start, index);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number)
                    : Long.parseLong(number);
        }

        private boolean match(String value) {
            if (!text.startsWith(value, index)) {
                return false;
            }

            index += value.length();
            return true;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw invalidJson("Expected '" + expected + "'.");
            }

            index++;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException invalidJson(String message) {
            return new IllegalArgumentException(
                    "Invalid API JSON at character " + index + ": " + message
            );
        }
    }
}
