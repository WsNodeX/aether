package io.aether.demo;

import io.aether.core.ToolHandler;
import io.aether.core.ToolResult;
import io.aether.json.Json;
import io.aether.server.McpServer;

import java.util.Map;

/**
 * 演示服务器：把几个纯本地工具（无需任何 API Key）注册到 Aether，然后对外提供 MCP 服务。
 *
 * <pre>
 * # 作为 stdio MCP server 启动，交给 Claude Desktop 等客户端连接
 * java -cp build io.aether.demo.McpServerDemo
 *
 * # 或打印一次示例交互（无需客户端），方便验证协议与工具
 * java -cp build io.aether.demo.McpServerDemo --demo
 * </pre>
 */
public final class McpServerDemo {

    private McpServerDemo() {
    }

    public static void main(String[] args) throws Exception {
        McpServer server = buildDemoServer();
        if (args.length > 0 && "--demo".equals(args[0])) {
            printSampleExchange(server);
            return;
        }
        System.out.println("Aether demo MCP server 已就绪（stdio）。交给任意 MCP 客户端即可调用以下工具。");
        server.run();
    }

    static McpServer buildDemoServer() {
        ToolHandler calculator = args -> {
            double a = asNumber(args.get("a"));
            double b = asNumber(args.get("b"));
            String op = String.valueOf(args.getOrDefault("op", "add"));
            double r = switch (op) {
                case "add" -> a + b;
                case "sub" -> a - b;
                case "mul" -> a * b;
                case "div" -> a / b;
                default -> throw new IllegalArgumentException("未知运算符: " + op);
            };
            return ToolResult.text(a + " " + op + " " + b + " = " + r);
        };

        ToolHandler currentTime = args -> ToolResult.text(
                "服务器当前时间(ISO-8601): " + java.time.OffsetDateTime.now().toString());

        ToolHandler wordCount = args -> {
            String text = String.valueOf(args.getOrDefault("text", ""));
            return ToolResult.text("字符数(忽略空白): " + text.replaceAll("\\s+", "").length());
        };

        ToolHandler echo = args -> ToolResult.text(String.valueOf(args.getOrDefault("message", "")));

        return McpServer.builder()
                .name("aether-demo")
                .version("1.0.0")
                .tool("calculator", "对两个数做加减乘除",
                        "{\"type\":\"object\",\"properties\":{"
                                + "\"a\":{\"type\":\"number\"},\"b\":{\"type\":\"number\"},"
                                + "\"op\":{\"type\":\"string\",\"enum\":[\"add\",\"sub\",\"mul\",\"div\"]}},"
                                + "\"required\":[\"a\",\"b\",\"op\"]}", calculator)
                .tool("current_time", "返回服务器当前时间(ISO-8601)",
                        "{\"type\":\"object\",\"properties\":{}}", currentTime)
                .tool("word_count", "统计文本字符数(忽略空白)",
                        "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},"
                                + "\"required\":[\"text\"]}", wordCount)
                .tool("echo", "原样返回输入文本",
                        "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},"
                                + "\"required\":[\"message\"]}", echo)
                .build();
    }

    private static double asNumber(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            return Double.parseDouble(s);
        }
        throw new IllegalArgumentException("需要数字参数");
    }

    private static void printSampleExchange(McpServer server) {
        String[] samples = {
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"calculator\",\"arguments\":{\"a\":6,\"b\":7,\"op\":\"mul\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"word_count\",\"arguments\":{\"text\":\"hello aether\"}}}"
        };
        for (String s : samples) {
            System.out.println("→ " + s);
            Object resp = server.handleMessage(Json.parse(s));
            System.out.println("← " + Json.stringify(resp));
        }
    }
}
