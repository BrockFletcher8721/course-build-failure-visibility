package dev.learningops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
                comma = true;
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean comma = false;
            for (Object item : values) {
                if (comma) out.append(',');
                out.append(stringify(item));
                comma = true;
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 32) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int at;

        Parser(String source) { this.source = source; }
        boolean done() { return at == source.length(); }
        void space() { while (!done() && Character.isWhitespace(source.charAt(at))) at++; }

        Object value() {
            space();
            if (done()) throw new IllegalArgumentException("Expected JSON value");
            return switch (source.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            at++;
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                require(':');
                map.put(key, value());
                space();
            } while (take(','));
            require('}');
            return map;
        }

        private List<Object> array() {
            at++;
            List<Object> list = new ArrayList<>();
            space();
            if (take(']')) return list;
            do {
                list.add(value());
                space();
            } while (take(','));
            require(']');
            return list;
        }

        private String string() {
            require('\"');
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char ch = source.charAt(at++);
                if (ch == '\"') return out.toString();
                if (ch != '\\') { out.append(ch); continue; }
                if (done()) throw new IllegalArgumentException("Unfinished JSON escape");
                char escaped = source.charAt(at++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(source.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad JSON escape");
                }
            }
            throw new IllegalArgumentException("Unfinished JSON string");
        }

        private Object number() {
            int start = at;
            while (!done() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
            String token = source.substring(start, at);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, at)) throw new IllegalArgumentException("Bad JSON literal");
            at += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!done() && source.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
