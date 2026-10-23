package io.aether.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用的返回结果。
 *
 * <p>遵循 MCP 的 content blocks 约定：结果是一组内容块（此处只用到 text 块），
 * 并通过 {@code isError} 区分「业务层面的失败」（如参数非法）与「成功」。
 */
public final class ToolResult {

    private final List<Map<String, Object>> content;
    private final boolean isError;

    private ToolResult(List<Map<String, Object>> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    /** 成功返回一段文本。 */
    public static ToolResult text(String text) {
        return of(text, false);
    }

    /** 业务失败：返回一段错误信息，标记 {@code isError=true}。 */
    public static ToolResult error(String text) {
        return of(text, true);
    }

    private static ToolResult of(String text, boolean isError) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        blocks.add(block);
        return new ToolResult(blocks, isError);
    }

    /** 转为 MCP 期望的响应对象：{content:[...], isError:bool}。 */
    public Map<String, Object> toJson() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("content", content);
        r.put("isError", isError);
        return r;
    }
}
