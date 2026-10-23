package io.aether.server;

import io.aether.core.ToolResult;
import io.aether.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MCP 服务端协议处理的单元测试（不依赖任何 I/O，直接驱动 handleMessage）。 */
class McpServerTest {

    private static McpServer sampleServer() {
        return McpServer.builder()
                .name("test-server")
                .version("0.0.1")
                .tool("add", "两数相加",
                        "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"number\"},\"b\":{\"type\":\"number\"}},"
                                + "\"required\":[\"a\",\"b\"]}",
                        args -> {
                            double a = ((Number) args.get("a")).doubleValue();
                            double b = ((Number) args.get("b")).doubleValue();
                            return ToolResult.text(a + b + "");
                        })
                .tool("boom", "永远抛异常", "{\"type\":\"object\",\"properties\":{}}",
                        args -> {
                            throw new RuntimeException("kaboom");
                        })
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(McpServer s, String json) {
        return (Map<String, Object>) s.handleMessage(Json.parse(json));
    }

    @Test
    void initializeReturnsProtocolAndServerInfo() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertEquals("2.0", resp.get("jsonrpc"));
        assertEquals(1, resp.get("id"));
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertEquals(McpServer.PROTOCOL_VERSION, result.get("protocolVersion"));
        Map<?, ?> info = (Map<?, ?>) result.get("serverInfo");
        assertEquals("test-server", info.get("name"));
        assertEquals("0.0.1", info.get("version"));
        Map<?, ?> caps = (Map<?, ?>) result.get("capabilities");
        assertNotNull(caps.get("tools"));
    }

    @Test
    void pingReturnsEmptyResult() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}");
        assertEquals(2, resp.get("id"));
        assertEquals(0, ((Map<?, ?>) resp.get("result")).size());
    }

    @Test
    void toolsListReturnsRegisteredTools() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        List<?> tools = (List<?>) result.get("tools");
        assertEquals(2, tools.size());
        Map<?, ?> first = (Map<?, ?>) tools.get(0);
        assertEquals("add", first.get("name"));
        assertNotNull(first.get("inputSchema"));
    }

    @Test
    void toolCallSuccessReturnsTextContent() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s,
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"add\",\"arguments\":{\"a\":2,\"b\":3}}}");
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertFalse((Boolean) result.get("isError"));
        List<?> content = (List<?>) result.get("content");
        assertEquals("5.0", ((Map<?, ?>) content.get(0)).get("text"));
    }

    @Test
    void toolCallExceptionBecomesErrorContent() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s,
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}");
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertTrue((Boolean) result.get("isError"));
        List<?> content = (List<?>) result.get("content");
        String text = (String) ((Map<?, ?>) content.get(0)).get("text");
        assertTrue(text.contains("kaboom"));
    }

    @Test
    void unknownToolIsJsonRpcError() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s,
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"nope\",\"arguments\":{}}}");
        Map<String, Object> err = (Map<String, Object>) resp.get("error");
        assertEquals(-32602, err.get("code"));
        assertTrue(((String) err.get("message")).contains("unknown tool"));
    }

    @Test
    void missingParamsIsJsonRpcError() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\"}");
        Map<String, Object> err = (Map<String, Object>) resp.get("error");
        assertEquals(-32602, err.get("code"));
    }

    @Test
    void notificationReturnsNull() {
        McpServer s = sampleServer();
        Map<?, ?> resp = s.handleMessage(
                Json.parse("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertNull(resp);
    }

    @Test
    void methodNotFoundIsJsonRpcError() {
        McpServer s = sampleServer();
        Map<String, Object> resp = call(s, "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"bogus/method\"}");
        Map<String, Object> err = (Map<String, Object>) resp.get("error");
        assertEquals(-32601, err.get("code"));
    }

    @Test
    void invalidRequestNonMapIsJsonRpcError() {
        McpServer s = sampleServer();
        Map<String, Object> resp = s.handleMessage("not-a-map");
        Map<String, Object> err = (Map<String, Object>) resp.get("error");
        assertEquals(-32600, err.get("code"));
    }
}
