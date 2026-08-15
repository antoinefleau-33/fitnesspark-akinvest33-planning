package dev.poc.musichud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parseur JSON minimal.
 *
 * <p>Minecraft embarque Gson, mais sa version change d'une version du jeu à l'autre et le mod
 * casserait au moindre décalage. Ces 120 lignes n'ont aucune dépendance : elles se compilent avec
 * un simple {@code javac}, ce qui permet aussi de tester le pont sans lancer le jeu.
 */
final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Object value = new MiniJson(text).readValue();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("objet JSON attendu");
        }
        return (Map<String, Object>) value;
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("fin de document inattendue");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{': return readMap();
            case '[': return readList();
            case '"': return readString();
            case 't': return readLiteral("true", Boolean.TRUE);
            case 'f': return readLiteral("false", Boolean.FALSE);
            case 'n': return readLiteral("null", null);
            default: return readNumber();
        }
    }

    private Map<String, Object> readMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;                       // '{'
        skipWhitespace();
        if (src.charAt(pos) == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            pos++;                   // ':'
            map.put(key, readValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == '}') return map;
            if (c != ',') throw new IllegalArgumentException("',' ou '}' attendu");
        }
    }

    private List<Object> readList() {
        List<Object> list = new ArrayList<>();
        pos++;                       // '['
        skipWhitespace();
        if (src.charAt(pos) == ']') { pos++; return list; }
        while (true) {
            list.add(readValue());
            skipWhitespace();
            char c = src.charAt(pos++);
            if (c == ']') return list;
            if (c != ',') throw new IllegalArgumentException("',' ou ']' attendu");
        }
    }

    private String readString() {
        pos++;                       // '"'
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char escaped = src.charAt(pos++);
            switch (escaped) {
                case '"': case '\\': case '/': sb.append(escaped); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw new IllegalArgumentException("échappement invalide");
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
        String text = src.substring(start, pos);
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
            return Double.parseDouble(text);
        }
        return Long.parseLong(text);
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) throw new IllegalArgumentException("littéral invalide");
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    // -- Accès typés, tolérants ---------------------------------------------------------------

    static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    static long num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Double) return ((Double) value).longValue();
        return 0L;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> map, String key) {
        Object value = map.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) out.add((Map<String, Object>) item);
            }
        }
        return out;
    }
}
