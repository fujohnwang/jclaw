package com.jclaw.skill;

import java.nio.file.Path;

/**
 * Parsed skill metadata from SKILL.md frontmatter.
 * Body content is loaded lazily on activation.
 *
 * @see <a href="https://agentskills.io/specification">Agent Skills Specification</a>
 */
public record SkillDef(
        String name,
        String description,
        Path path
) {
    /**
     * Returns the SKILL.md file path.
     */
    public Path skillMdPath() {
        return path.resolve("SKILL.md");
    }
}
