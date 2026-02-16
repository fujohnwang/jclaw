package com.jclaw.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tool: execute shell commands on the host machine.
 * Mirrors OpenClaw's exec tool.
 */
public final class ExecTool {

    private ExecTool() {}

    public static FunctionTool create() {
        return FunctionTool.create(ExecTool.class, "exec");
    }

    @Schema(description = "Execute a shell command and return its output. Use for running CLI tools, scripts, etc.")
    public static Map<String, Object> exec(
            @Schema(name = "command", description = "The shell command to execute") String command,
            @Schema(name = "timeoutSeconds", description = "Timeout in seconds (default 30)") Integer timeoutSeconds
    ) {
        int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 30;
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Map.of(
                        "exitCode", -1,
                        "output", output.toString(),
                        "error", "Command timed out after " + timeout + " seconds"
                );
            }

            return Map.of(
                    "exitCode", process.exitValue(),
                    "output", output.toString().trim()
            );
        } catch (Exception e) {
            return Map.of(
                    "exitCode", -1,
                    "output", "",
                    "error", e.getMessage()
            );
        }
    }
}
