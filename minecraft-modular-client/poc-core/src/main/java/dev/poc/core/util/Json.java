package dev.poc.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parseur JSON minimal, sans dépendance. Volontaire : {@code poc-core} est chargé par le
 * classloader racine et partagé avec toutes les sessions de jeu ; y injecter Gson ou Jackson
 * créerait un conflit de version avec ceux que Minecraft embarque déjà (1.8.9 apporte Gson 2.2.4,
 * 1.20.1 Gson 2.10). Le shell ne doit rien exposer qui puisse entrer en collision.
 *
 * <p>Pour la production : remplacer par {@code jackson-jr} relocalisé (shadow/shade) sous un
 * package privé, ce qui donne les mêmes garanties d'isolation avec un parseur éprouvé.
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) { this.src = src; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("contenu résiduel à l'offset " + p.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("objet JSON attendu");
        return (Map<String, Object>) v;
    }

    private Object readValue() {
        skipWs();
        if (pos >= src.length()) throw new IllegalArgumentException("fin de document inattendue");
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            map.put(key, readValue());
            skipWs();
            char c = src.charAt(pos++);
            if (c == '}') return map;
            if (c != ',') throw new IllegalArgumentException("',' ou '}' attendu à " + (pos - 1));
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(readValue());
            skipWs();
            char c = src.charAt(pos++);
            if (c == ']') return list;
            if (c != ',') throw new IllegalArgumentException("',' ou ']' attendu à " + (pos - 1));
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char esc = src.charAt(pos++);
            switch (esc) {
                case '"', '\\', '/' -> sb.append(esc);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw new IllegalArgumentException("échappement invalide \\" + esc);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
        String num = src.substring(start, pos);
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("littéral invalide à " + pos);
        }
        pos += literal.length();
        return value;
    }

    private void expect(char c) {
        if (src.charAt(pos) != c) {
            throw new IllegalArgumentException("'" + c + "' attendu à l'offset " + pos);
        }
        pos++;
    }

    private char peek() { return src.charAt(pos); }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    // -- Accès typés, tolérants ---------------------------------------------------------------

    public static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v instanceof String s ? s : def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : def;
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (!(v instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> strMap(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (!(v instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        ((Map<String, Object>) map).forEach((key, val) -> {
            if (val instanceof String s) out.put(key, s);
        });
        return out;
    }
}
