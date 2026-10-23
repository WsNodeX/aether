package io.aether.server;

import io.aether.core.ToolHandler;
import io.aether.core.ToolResult;
import io.aether.json.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 MCP（Model Context Protocol）服务端框架。
 *
 * <p>实现 MCP 的核心能力：基于 stdio 的 JSON-RPC 2.0 传输、工具（tools）的注册与调用，
 * 让你把一个普通 Java 方法暴露给任意支持 MCP 的 AI 客户端（Claude Desktop 等），
 * 全程零依赖、零网络库、零 JSON 库。
 *
 * <p>协议细节做了收敛：聚焦能跑通「客户端列工具 → 调工具」的最小可用子集，而非完整规范，
 * 但协议版本号、capabilities、initialize 握手均与标准对齐。
 */
public final class McpServer {

    /** 对齐的 MCP 协议版本（2024-11-05）。 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private final String name;
    private final String version;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    private McpServer(String name, String version) {
        this.name = name;
        this.version = version;
    }

    /** 启动 stdio 事件循环：逐行读 JSON-RPC，写回响应。 */
    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> response = handleMessage(Json.parse(line));
                if (response != null) {
                    out.write(Json.stringify(response));
                    out.write('\n');
                    out.flush();
                }
            } catch (RuntimeException e) {
                Map<String, Object> resp = errorResponse(idFromRaw(line), -32700, "parse error: " + e.getMessage());
                out.write(Json.stringify(resp));
                out.write('\n');
                out.flush();
            }
        }
    }

    /**
     * 处理一条 JSON-RPC 消息（不接触 I/O，便于单元测试）。
     * 返回响应对象；对通知（notification，无 id）返回 {@code null}。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleMessage(Object raw) {
        if (!(raw instanceof Map)) {
            return errorResponse(null, -32600, "invalid request");
        }
        Map<String, Object> message = (Map<String, Object>) raw;
        Object id = message.get("id");
        String method = (String) message.get("method");

        if (method == null) {
            return errorResponse(id, -32600, "missing method");
        }

        switch (method) {
            case "initialize":
                return successResponse(id, initializeResult());
            case "ping":
                return successResponse(id, emptyObject());
            case "shutdown":
                return successResponse(id, null);
            case "tools/list":
                return successResponse(id, toolsListResult());
            case "tools/call":
                return handleToolCall(id, (Map<String, Object>) message.get("params"));
            case "notifications/initialized":
            case "notifications/cancelled":
            case "exit":
                return null; // 通知：无需响应
            default:
                return errorResponse(id, -32601, "method not found: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Object id, Map<String, Object> params) {
        if (params == null) {
            return errorResponse(id, -32602, "missing params");
        }
        String toolName = (String) params.get("name");
        if (toolName == null) {
            return errorResponse(id, -32602, "missing tool name");
        }
        RegisteredTool tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "unknown tool: " + toolName);
        }
        Object args = params.get("arguments");
        Map<String, Object> argMap = (args instanceof Map) ? (Map<String, Object>) args : new LinkedHashMap<>();
        try {
            ToolResult result = tool.handler().handle(argMap);
            return successResponse(id, result.toJson());
        } catch (Exception e) {
            return successResponse(id, ToolResult.error(
                    "tool " + toolName + " failed: " + e.getMessage()).toJson());
        }
    }

    private Map<String, Object> initializeResult() {
        Map<String, Object> caps = new LinkedHashMap<>();
        Map<String, Object> toolsCap = new LinkedHashMap<>();
        toolsCap.put("listChanged", false);
        caps.put("tools", toolsCap);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", name);
        serverInfo.put("version", version);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", caps);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private Map<String, Object> toolsListResult() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RegisteredTool t : tools.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.name());
            entry.put("description", t.description());
            entry.put("inputSchema", t.inputSchema());
            list.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", list);
        return result;
    }

    private static Map<String, Object> successResponse(Object id, Object result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", result);
        return r;
    }

    private static Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        r.put("error", err);
        return r;
    }

    private static Map<String, Object> emptyObject() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Object idFromRaw(String raw) {
        try {
            Map<String, Object> m = (Map<String, Object>) Json.parse(raw);
            return m.get("id");
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ----- 构建器 -----

    public static Builder builder() {
        return new Builder();
    }

    /** 已注册工具（包内可见，供测试 / 自测使用）。 */
    Map<String, RegisteredTool> registeredTools() {
        return tools;
    }

    RegisteredTool registerInternal(String name, String description,
                                   String inputSchemaJson, ToolHandler handler) {
        Object parsed;
        try {
            parsed = Json.parse(inputSchemaJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("inputSchema 不是合法 JSON 对象: " + e.getMessage());
        }
        Map<String, Object> schema = (parsed instanceof Map) ? (Map<String, Object>) parsed : emptyObject();
        RegisteredTool t = new RegisteredTool(name, description, schema, handler);
        tools.put(name, t);
        return t;
    }

    /** 流式构建器：配置名称版本、注册工具，最终 {@link #build()} 得到服务端。 */
    public static final class Builder {
        private String name = "aether";
        private String version = "0.1.0";
        private final List<ToolDef> defs = new ArrayList<>();

        public Builder name(String n) {
            this.name = n;
            return this;
        }

        public Builder version(String v) {
            this.version = v;
            return this;
        }

        public Builder tool(String toolName, String description, String inputSchemaJson, ToolHandler handler) {
            defs.add(new ToolDef(toolName, description, inputSchemaJson, handler));
            return this;
        }

        public McpServer build() {
            McpServer server = new McpServer(name, version);
            for (ToolDef d : defs) {
                server.registerInternal(d.name, d.description, d.schema, d.handler);
            }
            return server;
        }

        private record ToolDef(String name, String description, String schema, ToolHandler handler) {
        }
    }
}
