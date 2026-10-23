package io.aether.server;

import io.aether.core.ToolHandler;

import java.util.Map;

/** 已注册的工具：名称、描述、入参 JSON Schema、处理器。 */
record RegisteredTool(String name, String description, Map<String, Object> inputSchema, ToolHandler handler) {
}
