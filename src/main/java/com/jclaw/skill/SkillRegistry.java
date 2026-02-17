package com.jclaw.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Scans ~/.jclaw/skills/ for Agent Skills (agentskills.io spec),
 * maintains an in-memory index, and watches for directory changes
 * so new skills are available on the next conversation.
 */
public final class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private static final String SKILL_MD = "SKILL.md";

    private final Path skillsDir;
    private final Map<String, SkillDef> skills = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    public SkillRegistry(Path skillsDir) {
        this.skillsDir = skillsDir;
        scan();
        startWatcher();
    }

    /**
     * Current version — incremented on every directory change.
     * Used by AgentRegistry to detect when agents need rebuilding.
     */
    public long version() {
        return version.get();
    }

    public SkillDef getSkill(String name) {
        return skills.get(name);
    }

    public Collection<SkillDef> allSkills() {
        return skills.values();
    }

    /**
     * Get skills available to an agent. If agentSkills is null or empty, all skills are available.
     * If agentSkills contains "all", all skills are available.
     * Otherwise, only the listed skills are returned.
     */
    public List<SkillDef> resolveSkills(List<String> agentSkills) {
        if (agentSkills == null || agentSkills.isEmpty() || agentSkills.contains("all")) {
            return List.copyOf(skills.values());
        }
        return agentSkills.stream()
                .map(skills::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Load the full SKILL.md body content (everything after frontmatter).
     * Called on skill activation, not at startup.
     */
    public String loadBody(SkillDef skill) {
        try {
            String content = Files.readString(skill.skillMdPath());
            // Strip YAML frontmatter (between --- delimiters)
            int firstDelim = content.indexOf("---");
            if (firstDelim >= 0) {
                int secondDelim = content.indexOf("---", firstDelim + 3);
                if (secondDelim >= 0) {
                    return content.substring(secondDelim + 3).strip();
                }
            }
            return content.strip();
        } catch (IOException e) {
            log.error("Failed to load SKILL.md body for skill '{}': {}", skill.name(), e.getMessage());
            return "";
        }
    }

    // ── Scanning ────────────────────────────────────────────────────────

    private void scan() {
        skills.clear();
        if (!Files.isDirectory(skillsDir)) {
            log.debug("Skills directory does not exist: {}", skillsDir);
            return;
        }
        try (Stream<Path> dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory).forEach(this::tryLoadSkill);
        } catch (IOException e) {
            log.error("Failed to scan skills directory: {}", e.getMessage());
        }
        log.info("SkillRegistry scanned: {} skill(s) found", skills.size());
        version.incrementAndGet();
    }

    private void tryLoadSkill(Path dir) {
        Path skillMd = dir.resolve(SKILL_MD);
        if (!Files.isRegularFile(skillMd)) {
            log.debug("Skipping directory without SKILL.md: {}", dir);
            return;
        }
        try {
            String content = Files.readString(skillMd);
            String name = extractFrontmatter(content, "name");
            String description = extractFrontmatter(content, "description");
            if (name == null || name.isBlank()) {
                log.warn("SKILL.md missing 'name' in {}", dir);
                return;
            }
            if (description == null || description.isBlank()) {
                log.warn("SKILL.md missing 'description' in {}", dir);
                return;
            }
            skills.put(name, new SkillDef(name, description, dir));
            log.info("Loaded skill: {} — {}", name, description);
        } catch (IOException e) {
            log.error("Failed to parse SKILL.md in {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Simple frontmatter value extractor. Handles single-line values only.
     */
    private static String extractFrontmatter(String content, String key) {
        int start = content.indexOf("---");
        if (start < 0) return null;
        int end = content.indexOf("---", start + 3);
        if (end < 0) return null;
        String frontmatter = content.substring(start + 3, end);
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith(key + ":")) {
                String val = trimmed.substring(key.length() + 1).strip();
                // Remove surrounding quotes if present
                if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                return val;
            }
        }
        return null;
    }

    // ── File Watcher ────────────────────────────────────────────────────

    private void startWatcher() {
        if (!Files.isDirectory(skillsDir)) return;
        Thread.startVirtualThread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                skillsDir.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                log.info("SkillRegistry watching for changes: {}", skillsDir);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watcher.take();
                    // Debounce: consume all pending events
                    key.pollEvents();
                    key.reset();
                    log.info("Skills directory changed, rescanning...");
                    scan();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("SkillRegistry watcher interrupted");
            } catch (IOException e) {
                log.error("SkillRegistry watcher failed: {}", e.getMessage());
            }
        });
    }
}
