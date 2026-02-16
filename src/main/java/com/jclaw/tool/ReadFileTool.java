package com.jclaw.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tool: read file contents.
 */
public final class ReadFileTool {

    private ReadFileTool() {}

    public static FunctionTool create() {
        return FunctionTool.create(ReadFileTool.class, "readFile");
    }

    @Schema(description = "Read the contents of a file at the given path")
    public static Map<String, String> readFile(
            @Schema(name = "path", description = "Absolute or relative file path to read") String filePath
    ) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            if (!Files.exists(path)) {
                return Map.of("error", "File not found: " + path);
            }
            if (Files.isDirectory(path)) {
                return Map.of("error", "Path is a directory: " + path);
            }
            long size = Files.size(path);
            if (size > 512_000) {
                return Map.of("error", "File too large: " + size + " bytes (max 512KB)");
            }
            String content = Files.readString(path);
            return Map.of("path", path.toString(), "content", content);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
