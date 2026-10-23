package io.aether.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖极简 JSON 解析 / 序列化。
 *
 * <p>只覆盖 MCP 协议与工具参数所需的子集：对象、数组、字符串、数字、布尔、null，
 * 足以驱动 JSON-RPC 2.0，无需引入任何第三方 JSON 库。
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.parseValue();
        p.skipWs();
        if (!p.end()) {
            throw new JsonException("JSON 尾部存在多余字符");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        boolean end() {
            return i >= s.length();
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (end()) {
                throw new JsonException("JSON 意外结束");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new JsonException("无法解析的 JSON 值，位置 " + i);
            }
        }

        Map<String, Object> parseObject() {
            expectChar('{');
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw new JsonException("对象键必须是字符串");
                }
                String key = parseString();
                skipWs();
                expectChar(':');
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw new JsonException("对象缺少逗号或右大括号");
                }
            }
            return m;
        }

        List<Object> parseArray() {
            expectChar('[');
            ArrayList<Object> a = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                i++;
                return a;
            }
            while (true) {
                Object v = parseValue();
                a.add(v);
                skipWs();
                char c = next();
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw new JsonException("数组缺少逗号或右中括号");
                }
            }
            return a;
        }

        String parseString() {
            expectChar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (end()) {
                    throw new JsonException("字符串未闭合");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (end()) {
                        throw new JsonException("转义未闭合");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) {
                                throw new JsonException("unicode 转义不完整");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonException("不支持的转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            String num = s.substring(start, i);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                long l = Long.parseLong(num);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException e) {
                throw new JsonException("非法数字: " + num);
            }
        }

        char peek() {
            if (end()) {
                throw new JsonException("JSON 意外结束");
            }
            return s.charAt(i);
        }

        char next() {
            if (end()) {
                throw new JsonException("JSON 意外结束");
            }
            return s.charAt(i++);
        }

        void expectChar(char c) {
            if (next() != c) {
                throw new JsonException("期望字符 '" + c + "'");
            }
        }

        void expect(String lit) {
            if (i + lit.length() > s.length() || !s.startsWith(lit, i)) {
                throw new JsonException("期望 '" + lit + "'");
            }
            i += lit.length();
        }
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String) {
            writeString((String) v, sb);
            return;
        }
        if (v instanceof Boolean) {
            sb.append(v.toString());
            return;
        }
        if (v instanceof Number) {
            writeNumber(((Number) v).doubleValue(), sb);
            return;
        }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                writeString(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
                first = false;
            }
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<Object>) v) {
                if (!first) {
                    sb.append(',');
                }
                write(item, sb);
                first = false;
            }
            sb.append(']');
            return;
        }
        writeString(v.toString(), sb);
    }

    private static void writeNumber(double d, StringBuilder sb) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
            return;
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append(Long.toString((long) d));
        } else {
            sb.append(Double.toString(d));
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** JSON 解析失败时的异常。 */
    public static final class JsonException extends RuntimeException {
        JsonException(String msg) {
            super(msg);
        }
    }
}
