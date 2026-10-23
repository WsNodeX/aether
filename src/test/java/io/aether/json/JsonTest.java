package io.aether.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 零依赖 JSON 解析 / 序列化的单元测试。 */
class JsonTest {

    @Test
    void parseEmptyObject() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{}");
        assertEquals(0, m.size());
    }

    @Test
    void parseNestedObject() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"a\":1,\"b\":{\"c\":\"x\"}}");
        assertEquals(1, m.get("a"));
        Map<?, ?> b = (Map<?, ?>) m.get("b");
        assertEquals("x", b.get("c"));
    }

    @Test
    void parseArray() {
        List<?> a = (List<?>) Json.parse("[1,2,3]");
        assertEquals(3, a.size());
        assertEquals(2, a.get(1));
    }

    @Test
    void parseStringEscapes() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"k\":\"a\\\"b\\\\c\\nd\\te\\u0041\"}");
        assertEquals("a\"b\\c\nd\teA", m.get("k"));
    }

    @Test
    void parseNumbers() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"i\":42,\"l\":9000000000,\"f\":3.14,\"e\":1.5e3}");
        assertEquals(42, m.get("i"));
        assertEquals(9000000000L, m.get("l"));
        assertEquals(3.14, (Double) m.get("f"), 1e-9);
        assertEquals(1500.0, (Double) m.get("e"), 1e-9);
    }

    @Test
    void parseBooleanAndNull() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"t\":true,\"f\":false,\"n\":null}");
        assertEquals(Boolean.TRUE, m.get("t"));
        assertEquals(Boolean.FALSE, m.get("f"));
        assertNull(m.get("n"));
    }

    @Test
    void parseNegativeAndLeadingWhitespace() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("  {\"x\": -17 } ");
        assertEquals(-17, m.get("x"));
    }

    @Test
    void stringifyRoundtripObject() {
        String src = "{\"name\":\"aether\",\"count\":3,\"flag\":true}";
        assertEquals(src, Json.stringify(Json.parse(src)));
    }

    @Test
    void stringifyRoundtripArray() {
        String src = "[1,\"two\",false,null,{\"nested\":[9]}]";
        assertEquals(src, Json.stringify(Json.parse(src)));
    }

    @Test
    void stringifyEscapesSpecialChars() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("s", "line1\nline2\ttab\"quote\\slash");
        String out = Json.stringify(m);
        // 重新解析回来应当一致
        assertEquals("line1\nline2\ttab\"quote\\slash", ((Map<?, ?>) Json.parse(out)).get("s"));
    }

    @Test
    void stringifyIntegerVsDouble() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("i", 10);
        m.put("d", 10.5);
        String out = Json.stringify(m);
        Map<?, ?> back = (Map<?, ?>) Json.parse(out);
        assertInstanceOf(Integer.class, back.get("i"));
        assertInstanceOf(Double.class, back.get("d"));
    }

    @Test
    void parseRejectsTrailingGarbage() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{} garbage"));
    }

    @Test
    void parseRejectsUnclosedString() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"k\":\"unclosed"));
    }

    @Test
    void parseRejectsInvalidValue() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{:}"));
    }

    @Test
    void parseRejectsEmptyInput() {
        assertThrows(Json.JsonException.class, () -> Json.parse(""));
    }

    @Test
    void parseRejectsWhitespaceOnly() {
        assertThrows(Json.JsonException.class, () -> Json.parse("   "));
    }
}
