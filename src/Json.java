import java.util.*;

public class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; this.i = 0; }

    public static Object parse(String text) {
        Json p = new Json(text == null ? "" : text.trim());
        p.ws();
        Object v = p.value();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Map<String, Object> m, String k, String d) {
        Object v = m.get(k);
        return v == null ? d : String.valueOf(v);
    }

    public static int num(Map<String, Object> m, String k, int d) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return d; }
    }

    public static double numD(Map<String, Object> m, String k, double d) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return d; }
    }

    public static boolean bool(Map<String, Object> m, String k, boolean d) {
        Object v = m.get(k);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.equalsIgnoreCase("true") || s.equals("1")) return true;
            if (s.equalsIgnoreCase("false") || s.equals("0") || s.isEmpty()) return false;
        }
        return d;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object value() {
        ws();
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') { i += 4; return Boolean.TRUE; }
        if (c == 'f') { i += 5; return Boolean.FALSE; }
        if (c == 'n') { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (i < s.length()) {
            ws();
            String k = string();
            ws();
            i++;
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == '}') i++;
            break;
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (i < s.length()) {
            l.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
            if (i < s.length() && s.charAt(i) == ']') i++;
            break;
        }
        return l;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        catch (Exception ex) { sb.append('u'); }
                    }
                    default -> sb.append(e);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        try {
            double d = Double.parseDouble(s.substring(st, i));
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return (long) d;
            return d;
        } catch (Exception e) { return 0L; }
    }

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb) {
        if (o == null) sb.append("null");
        else if (o instanceof String s) quote(s, sb);
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) sb.append(o);
        else if (o instanceof Number n) sb.append(n.doubleValue() == Math.rint(n.doubleValue()) ? n.longValue() : n);
        else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean f = true;
            for (var e : m.entrySet()) {
                if (!f) sb.append(',');
                f = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) sb.append(',');
                write(l.get(k), sb);
            }
            sb.append(']');
        } else quote(String.valueOf(o), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
