package io.aether.core;

import java.util.Map;

/**
 * 工具处理器：把一组结构化的参数映射成 {@link ToolResult}。
 *
 * <p>实现里抛出任意异常都会被框架捕获并转为 {@code isError=true} 的结果，
 * 因此处理器只需专注业务逻辑，不必自己处理错误包装。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult handle(Map<String, Object> args) throws Exception;
}
