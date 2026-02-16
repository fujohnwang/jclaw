package com.jclaw.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tool: write content to a file.
 */
public final class WriteFileTool {

    private WriteFileTool() {}

    public static FunctionTool create() {
        return FunctionTool.create(WriteFileTool.class, "writeFile");
    }

    @Schema(description = "Write content to a file, creating parent directories if needed. Overwrites existing content.")
    public static Map<String, String> writeFile(
            @Schema(name = "path", description = "Absolute or relative file path to write") String filePath,
            @Schema(name = "content", description = "Content to write to the file") String content
    ) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return Map.of("path", path.toString(), "status", "written", "bytes", String.valueOf(content.length()));
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
