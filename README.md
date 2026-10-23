<div align="center">

# Aether

### 零依赖的 Java MCP 服务端框架 —— 把任意 Java 方法变成 AI 工具

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/WsNodeX/aether/actions)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.java.com)
[![MCP](https://img.shields.io/badge/MCP-2024--11--05-9cf)](https://modelcontextprotocol.io)
[![Dependencies](https://img.shields.io/badge/dependencies-0-lightgrey)](build.gradle)

**不引入任何第三方库，仅凭 JDK 标准库，实现一个可对接 Claude Desktop 等任意 MCP 客户端的工具服务器。**

</div>

---

## 这是什么

[Aether](https://github.com/WsNodeX/aether) 是一个极简的 **Model Context Protocol（模型上下文协议）** 服务端框架，用纯 Java 标准库写成。它帮你把一个普通的 Java 方法，通过 `stdio` 上的 JSON-RPC 2.0 协议，暴露给任意支持 MCP 的 AI 客户端 —— 比如 Claude Desktop、各种 Agent 框架。

整个项目 **零运行时依赖**：没有 Jackson、没有 Netty、没有 Spring，连 JSON 解析器都是手写的。这意味着你可以把它的源码直接拷进任何 JVM 项目里，或者把它当作学习 MCP 协议的最佳最小实现来读。

> 💡 如果你正在准备 AI 方向的实习 / 校招，亲手实现一个 MCP server 是比“调通一个 SDK”更有说服力的项目：它证明你真的理解 Agent 与工具调用的底层机制，而不是只会调 API。

---

## 为什么要从零写

市面上的 MCP SDK（官方 `mcp` 系列、各类封装）动辄拉进来一整套依赖树。对学习者来说，那是一堵墙——你只看到一堆封装，却看不清协议本身长什么样。

Aether 的目标正好相反：**把 MCP 拆到不能再拆**。

- 协议就是 `stdio` 上的一行行 JSON-RPC 2.0 消息；
- “工具”就是一个函数：`Map<String,Object> args -> ToolResult`；
- 客户端列工具、调工具，服务端回响应 —— 仅此而已。

当你能把这套东西用几百行标准库代码跑通，你对 “AI 怎么用工具” 的理解就从“黑盒”变成了“白盒”。

---

## ✨ 特性

- 🪶 **零依赖**：仅用 JDK 标准库，无第三方 jar。
- 🔌 **标准协议**：对齐 MCP `2024-11-05`，完整的 `initialize` / `ping` / `tools/list` / `tools/call` 握手与调用流程。
- 🧰 **类型安全的工具注册**：用 `Builder` 流式注册工具，自带 JSON Schema 校验。
- 🛡️ **健壮的错误处理**：工具抛异常会被框架捕获并转成 `isError` 结果，不会让整个进程崩溃。
- 📨 **通知支持**：正确处理 `notifications/*` 与 `exit`，对通知返回 `null`（不回包）。
- 🧪 **测试完备**：26 个 JUnit 5 单元测试覆盖解析、序列化与全部协议分支，CI 在 JDK 17 / 21 双版本跑绿。
- 📖 **可读即文档**：核心实现不到 400 行，注释即教程。

---

## 🏗️ 架构

```
                ┌─────────────────────────────┐
   AI 客户端     │        Aether Server         │      你的业务
 (Claude 等)     │                              │
        │ stdio │   ┌──────────┐  handleMessage │   ┌──────────────┐
        │ JSON──┼──▶│ McpServer│───────────────┼──▶│ ToolHandler   │
        │ RPC   │   │          │  (JSON-RPC 2.0)│   │  args ->      │
        │◀──────┼───│  router  │◀───────────────┼───│  ToolResult   │
        │       │   └──────────┘                │   └──────────────┘
        │       │   ┌──────────┐                │
        │       │   │   Json   │ 手写零依赖解析  │
        │       │   │ parse/    │ / 序列化       │
        │       │   │ stringify │                │
        │       │   └──────────┘                │
        └───────┴──────────────────────────────┘
```

数据流向：客户端发来一行 JSON-RPC → `McpServer.handleMessage` 解析并路由 → 命中 `ToolHandler` → 业务返回 `ToolResult` → 框架包成 MCP 约定的 `{content:[...], isError:bool}` 写回。全程不触碰任何网络库，因为 stdio 本身就是“管道”。

---

## 📦 安装与构建

需要 **JDK 17 或以上**。无需 Maven / Gradle（项目刻意保持零构建工具依赖），直接用 `javac` 即可编译。

```bash
# 克隆
git clone https://github.com/WsNodeX/aether.git
cd aether

# 编译（主代码 + 测试代码）
mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
javac -cp "build/classes:junit.jar" -d build/test-classes $(find src/test/java -name '*.java')
```

> 跑测试需要一个 JUnit 5 standalone runner：
> `curl -L -o junit.jar https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar`

---

## 🚀 快速开始

### 1. 跑一次示例交互（无需任何客户端）

```bash
java -cp "build/classes" io.aether.demo.McpServerDemo --demo
```

它会打印几轮「客户端请求 → 服务端响应」，你可以直接看到协议长什么样（见下方 [协议交互示例](#-协议交互示例)）。

### 2. 作为真正的 MCP server 启动

```bash
java -cp "build/classes" io.aether.demo.McpServerDemo
```

进程会阻塞在 stdio 上，等待任意 MCP 客户端连接并调用下面 4 个内置工具：

| 工具名         | 作用                  | 参数                              |
| -------------- | --------------------- | --------------------------------- |
| `calculator`   | 两数加减乘除          | `a:number, b:number, op:enum`     |
| `current_time` | 返回服务器当前时间    | 无                                |
| `word_count`   | 统计字符数（忽略空白）| `text:string`                     |
| `echo`         | 原样返回输入          | `message:string`                  |

### 3. 接入 Claude Desktop

在 Claude Desktop 的配置文件（`claude_desktop_config.json`）里加一段：

```json
{
  "mcpServers": {
    "aether-demo": {
      "command": "java",
      "args": [
        "-cp", "/绝对路径/aether/build/classes",
        "io.aether.demo.McpServerDemo"
      ]
    }
  }
}
```

重启 Claude Desktop 后，就能在工具列表里看到 `calculator` / `current_time` 等，并直接对话调用。

---

## 🧩 核心 API

三行就能注册一个工具：

```java
McpServer server = McpServer.builder()
    .name("my-server")
    .version("1.0.0")
    .tool("add", "两数相加",
          "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"number\"},\"b\":{\"type\":\"number\"}},\"required\":[\"a\",\"b\"]}",
          args -> {
              double a = ((Number) args.get("a")).doubleValue();
              double b = ((Number) args.get("b")).doubleValue();
              return ToolResult.text(a + b + "");
          })
    .build();

server.run();   // 启动 stdio 事件循环
```

- `McpServer.builder()` —— 流式配置名称、版本、工具。
- `.tool(name, description, inputSchemaJson, handler)` —— 注册工具，`inputSchemaJson` 是标准 JSON Schema，会在注册时被校验。
- `ToolHandler` —— 函数式接口 `Map<String,Object> args -> ToolResult`，抛任何异常都会被框架兜底。
- `ToolResult.text(...)` / `ToolResult.error(...)` —— 成功 / 业务失败两种结果。

想测试协议逻辑而不起进程？直接调 `server.handleMessage(Json.parse(json))` 拿回响应对象即可（测试里就是这么做的）。

---

## 🔍 协议交互示例

下面是 `McpServerDemo --demo` 的真实输出，逐字展示了 MCP 的握手与调用：

```
→ {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
← {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{"listChanged":false}},"serverInfo":{"name":"aether-demo","version":"1.0.0"}}}

→ {"jsonrpc":"2.0","id":2,"method":"tools/list"}
← {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"calculator","description":"对两个数做加减乘除","inputSchema":{"type":"object","properties":{"a":{"type":"number"},"b":{"type":"number"},"op":{"type":"string","enum":["add","sub","mul","div"]}},"required":["a","b","op"]}},{"name":"current_time","description":"返回服务器当前时间(ISO-8601)","inputSchema":{"type":"object","properties":{}}},{"name":"word_count","description":"统计文本字符数(忽略空白)","inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}},{"name":"echo","description":"原样返回输入文本","inputSchema":{"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}}]}}

→ {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"calculator","arguments":{"a":6,"b":7,"op":"mul"}}}
← {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"6.0 mul 7.0 = 42.0"}],"isError":false}}

→ {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"word_count","arguments":{"text":"hello aether"}}}
← {"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"字符数(忽略空白): 11"}],"isError":false}}
```

可以看到：客户端先 `initialize` 拿到协议版本与服务能力，再 `tools/list` 拉取工具清单，最后 `tools/call` 带着参数调用具体工具 —— 这就是 MCP 工具调用的标准三步。

---

## 🧪 测试

```bash
java -jar junit.jar execute \
  -cp "build/classes:build/test-classes:junit.jar" \
  --scan-class-path --details=tree
```

覆盖内容包括：

- `Json` 解析 / 序列化：嵌套对象、数组、转义、整数与浮点区分、各类非法输入报错；
- `McpServer` 协议：initialize、ping、tools/list、tools/call 成功、工具抛异常转 `isError`、未知工具 / 缺参数 / 未知方法 / 非法请求的错误码，以及通知返回 `null`。

CI 在 `ubuntu-latest` 上用 **JDK 17 与 21** 两个版本跑全部用例。

---

## 🗺️ 路线图

- [ ] 支持 `resources` 与 `prompts` 两类 MCP 原语
- [ ] 提供 `stdio` 之外的 `SSE` / `HTTP` 传输适配器
- [ ] 工具参数自动按 JSON Schema 做类型校验与默认值填充
- [ ] 可选的 `logging` 与 `completion` 能力
- [ ] 打包成可直接 `java -jar` 运行的 fat-free 单文件

---

## 🤝 贡献

欢迎提 Issue 与 PR。本地改完跑通测试（`--scan-class-path` 别忘了）再提交即可。设计原则是“能不加依赖就不加”，任何新功能都应先考虑能否用标准库解决。

---

## 📄 许可证

[MIT](LICENSE) © WsNodeX
