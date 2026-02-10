package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.WebSearchApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone CLI command to install AI coding assistant instruction files.
 *
 * <p>This command runs <b>without</b> Spring Boot context -- it is intercepted
 * in {@code main()} before Spring boots, so it executes instantly.</p>
 *
 * <p>Supports 11 target environments, each with its own file path and format:</p>
 * <ul>
 *   <li>Cursor, Antigravity, Droid, Kilo Code (YAML frontmatter + markdown in skills directory)</li>
 *   <li>Claude Code, OpenHands, Copilot, Windsurf (plain markdown)</li>
 *   <li>Cline, Roo Code (markdown in subdirectory)</li>
 *   <li>Mistral Vibe (custom prompt in .vibe/prompts/)</li>
 * </ul>
 */
@Command(
        name = "install-skill",
        mixinStandardHelpOptions = true,
        description = "Install web-search-api instructions for an AI coding assistant."
)
public class InstallSkillCommand implements Runnable {

    private static final String SKILL_NAME = "noetic";
    private static final String DESCRIPTION = "Search the web, crawl pages, extract PDF content, "
            + "chunk text, and query a local vector cache using Noetic. "
            + "Use when the user asks to search the internet, fetch a web page, extract content "
            + "from a URL, build a knowledge base, research a topic, or find cached information.";

    @Option(names = {"-t", "--target"}, defaultValue = "cursor",
            converter = TargetConverter.class,
            description = "Target environment: cursor, antigravity, droid, claude-code, openhands, copilot, windsurf, cline, roo, kilo, vibe (default: ${DEFAULT-VALUE})")
    private Target target;

    @Option(names = {"-d", "--project-dir"},
            description = "Project directory to install into (default: current directory)")
    private Path projectDir;

    @Option(names = {"-p", "--port"}, defaultValue = "8090",
            description = "Server port reflected in the generated instructions (default: ${DEFAULT-VALUE})")
    private int port;

    @Option(names = {"-f", "--force"}, defaultValue = "false",
            description = "Overwrite existing file without prompting")
    private boolean force;

    @Option(names = {"-l", "--list"}, defaultValue = "false",
            description = "List all supported targets and exit")
    private boolean list;

    /**
     * Supported target environments.
     *
     * <p>Each target specifies the relative path for the skill/instructions file,
     * whether it uses YAML frontmatter, and optionally a project-level MCP
     * configuration path (for editors that support {@code mcp.json}).</p>
     */
    enum Target {
        cursor(".cursor/skills/" + SKILL_NAME + "/SKILL.md", true, ".cursor/mcp.json"),
        antigravity(".agent/skills/" + SKILL_NAME + "/SKILL.md", true, null),
        droid(".factory/skills/" + SKILL_NAME + "/SKILL.md", true, null),
        claude_code("CLAUDE.md", false, null),
        openhands("AGENTS.md", false, null),
        copilot(".github/copilot-instructions.md", false, null),
        windsurf(".windsurfrules", false, null),
        cline(".clinerules/" + SKILL_NAME + ".md", false, null),
        roo(".roo/rules/" + SKILL_NAME + ".md", false, null),
        kilo(".kilocode/skills/" + SKILL_NAME + "/SKILL.md", true, ".kilocode/mcp.json"),
        vibe(".vibe/prompts/" + SKILL_NAME + ".md", false, null);

        final String relativePath;
        final boolean hasFrontmatter;
        /** Project-level MCP config path, or {@code null} if the target doesn't support it. */
        final String mcpConfigPath;

        Target(String relativePath, boolean hasFrontmatter, String mcpConfigPath) {
            this.relativePath = relativePath;
            this.hasFrontmatter = hasFrontmatter;
            this.mcpConfigPath = mcpConfigPath;
        }

        /** Display name with dashes instead of underscores. */
        String displayName() {
            return name().replace('_', '-');
        }
    }

    /** Converts CLI input (with dashes) to Target enum (with underscores). */
    static class TargetConverter implements CommandLine.ITypeConverter<Target> {
        @Override
        public Target convert(String value) {
            String normalized = value.replace('-', '_');
            try {
                return Target.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(
                        "Unknown target '" + value + "'. Use --list to see supported targets.");
            }
        }
    }

    @Override
    public void run() {
        if (list) {
            printTargets();
            return;
        }

        try {
            Path baseDir = resolveBaseDir();
            Path outputFile = baseDir.resolve(target.relativePath);

            if (Files.exists(outputFile) && !force) {
                System.err.println("File already exists: " + outputFile);
                System.err.println("Use --force to overwrite.");
                System.exit(1);
            }

            String content = buildContent(baseDir);

            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, content, StandardCharsets.UTF_8);

            System.out.println("Installed " + target.displayName() + " instructions:");
            System.out.println("  " + outputFile.toAbsolutePath());

            // Install project-level MCP config if the target supports it
            if (target.mcpConfigPath != null) {
                installMcpConfig(baseDir);
            }

            // Print MCP setup hint for targets that use non-JSON config (e.g. TOML)
            if (target == Target.vibe) {
                printVibeMcpHint(baseDir);
            }
        } catch (Exception e) {
            System.err.println("Failed to install: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Execute this command standalone (called from main() before Spring boots).
     */
    public static void execute(String[] args) {
        // Strip the leading "install-skill" arg if present
        String[] cmdArgs = args;
        if (args.length > 0 && "install-skill".equals(args[0])) {
            cmdArgs = new String[args.length - 1];
            System.arraycopy(args, 1, cmdArgs, 0, cmdArgs.length);
        }
        int exitCode = new CommandLine(new InstallSkillCommand()).execute(cmdArgs);
        if (exitCode != 0) {
            System.exit(exitCode); // only exit on failure; caller returns from main() on success
        }
    }

    // ---- internal ----

    private void printTargets() {
        System.out.println("Supported targets:");
        System.out.println();
        for (Target t : Target.values()) {
            System.out.printf("  %-14s  %s%n", t.displayName(), t.relativePath);
        }
        System.out.println();
        System.out.println("Usage: noetic install-skill --target=<target> [--project-dir=<dir>]");
    }

    private Path resolveBaseDir() {
        if (projectDir != null) {
            return projectDir.toAbsolutePath();
        }
        return Path.of(System.getProperty("user.dir"));
    }

    private String buildContent(Path baseDir) throws IOException {
        String core = loadCoreTemplate();

        // Resolve template variables
        String javaPath = detectJavaPath();
        String jarPath = baseDir.resolve("build/libs/noetic-" + WebSearchApplication.VERSION + ".jar")
                .toAbsolutePath().toString();
        String noeticBin = detectMcpCommand(baseDir); // native binary or script path

        core = core.replace("{{port}}", String.valueOf(port))
                   .replace("{{project_dir}}", baseDir.toAbsolutePath().toString())
                   .replace("{{java_path}}", javaPath)
                   .replace("{{jar_path}}", jarPath)
                   .replace("{{noetic_bin}}", noeticBin);

        if (target.hasFrontmatter) {
            return buildFrontmatterContent(core);
        } else {
            return core;
        }
    }

    private String buildFrontmatterContent(String core) {
        return """
                ---
                name: %s
                description: %s
                ---
                
                %s""".formatted(SKILL_NAME, DESCRIPTION, core);
    }

    private String loadCoreTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/instructions/core.md.template")) {
            if (is == null) {
                throw new IOException("Template not found on classpath: /instructions/core.md.template");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String detectJavaPath() {
        // 1. Check JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaBin = Path.of(javaHome, "bin", "java");
            if (Files.isExecutable(javaBin)) {
                return javaBin.toString();
            }
        }

        // 2. Check current JVM
        String currentJava = ProcessHandle.current().info().command().orElse(null);
        if (currentJava != null) {
            return currentJava;
        }

        // 3. Fallback
        return "java";
    }

    /**
     * Installs a project-level MCP configuration file for targets that support it.
     *
     * <p>If the config file already exists, its content is read and the
     * {@code noetic} server entry is merged into the existing {@code mcpServers}
     * object. If the file does not exist, a new config is created.</p>
     */
    private void installMcpConfig(Path baseDir) throws IOException {
        Path mcpFile = baseDir.resolve(target.mcpConfigPath);

        if (Files.exists(mcpFile) && !force) {
            System.err.println("MCP config already exists: " + mcpFile);
            System.err.println("Use --force to overwrite.");
            return;
        }

        String mcpJson = buildMcpConfig(baseDir);

        Files.createDirectories(mcpFile.getParent());
        Files.writeString(mcpFile, mcpJson, StandardCharsets.UTF_8);

        System.out.println("Installed " + target.displayName() + " MCP config:");
        System.out.println("  " + mcpFile.toAbsolutePath());
    }

    /**
     * Builds the MCP server configuration JSON.
     *
     * <p>Resolution order for the MCP command:</p>
     * <ol>
     *   <li>{@code noetic} on PATH (e.g. {@code ~/.local/bin/noetic})</li>
     *   <li>Native binary in the project build output</li>
     *   <li>{@code bin/mcp-server.sh} launcher script (fallback)</li>
     * </ol>
     */
    private String buildMcpConfig(Path baseDir) {
        String command = detectMcpCommand(baseDir);

        // If using the native binary directly, pass STDIO profile args.
        // The mcp-server.sh script handles these internally.
        boolean needsArgs = !command.endsWith("mcp-server.sh");

        if (needsArgs) {
            return """
                    {
                      "mcpServers": {
                        "%s": {
                          "command": "%s",
                          "args": ["--spring.profiles.active=stdio", "--spring.main.banner-mode=off"],
                          "disabled": false,
                          "alwaysAllow": []
                        }
                      }
                    }
                    """.formatted(SKILL_NAME, escapeJson(command));
        } else {
            return """
                    {
                      "mcpServers": {
                        "%s": {
                          "command": "%s",
                          "args": [],
                          "disabled": false,
                          "alwaysAllow": []
                        }
                      }
                    }
                    """.formatted(SKILL_NAME, escapeJson(command));
        }
    }

    /**
     * Detects the best command for launching the MCP server.
     */
    private String detectMcpCommand(Path baseDir) {
        // 1. Check for noetic on PATH
        try {
            var result = new ProcessBuilder("which", "noetic")
                    .redirectErrorStream(true).start();
            String output = new String(result.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (result.waitFor() == 0 && !output.isBlank()) {
                return output;
            }
        } catch (Exception ignored) {
            // Fall through
        }

        // 2. Check for native binary in build output
        Path nativeBin = baseDir.resolve("build/native/nativeCompile/noetic");
        if (Files.isExecutable(nativeBin)) {
            return nativeBin.toAbsolutePath().toString();
        }

        // 3. Fallback to mcp-server.sh
        return baseDir.resolve("bin/mcp-server.sh").toAbsolutePath().toString();
    }

    /** Minimal JSON string escaping for file paths (backslashes on Windows). */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Prints the TOML snippet for adding Noetic as an MCP server in Mistral
     * Vibe's {@code .vibe/config.toml}. Vibe uses TOML-based MCP config
     * rather than a separate JSON file, so we print a ready-to-paste snippet.
     */
    private void printVibeMcpHint(Path baseDir) {
        String command = detectMcpCommand(baseDir);
        boolean needsArgs = !command.endsWith("mcp-server.sh");

        System.out.println();
        System.out.println("To enable the MCP server, add this to your .vibe/config.toml:");
        System.out.println();
        System.out.println("  [[mcp_servers]]");
        System.out.printf("  name = \"%s\"%n", SKILL_NAME);
        System.out.println("  transport = \"stdio\"");
        System.out.printf("  command = \"%s\"%n", command);
        if (needsArgs) {
            System.out.println("  args = [\"--spring.profiles.active=stdio\", \"--spring.main.banner-mode=off\"]");
        } else {
            System.out.println("  args = []");
        }
    }
}
