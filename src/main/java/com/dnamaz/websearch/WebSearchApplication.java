package com.dnamaz.websearch;

import com.dnamaz.websearch.adapter.cli.InstallSkillCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Noetic - Web search, crawl, and knowledge cache for AI coding assistants.
 *
 * <p>Supports three operational modes selected at launch:</p>
 * <ul>
 *   <li><b>MCP</b> (default) - STDIO/SSE transport for LLM clients</li>
 *   <li><b>REST</b> - HTTP API on configurable port</li>
 *   <li><b>CLI</b> - One-shot command execution, JSON to stdout</li>
 * </ul>
 *
 * <p>Fast-path commands ({@code install-skill}, {@code --version}, {@code --help})
 * are handled by {@link FastPathRunner} which works in both JVM and native image
 * modes via the standard Spring Boot {@link CommandLineRunner} lifecycle.</p>
 */
@SpringBootApplication
@EnableScheduling
public class WebSearchApplication {

    static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        // Fast-path commands run BEFORE Spring context starts.
        // No beans, no Tomcat, no Lucene, no ONNX — just print/write and exit.
        // System.exit() is safe here because Spring hasn't started yet,
        // so there are no shutdown hooks to hang on.
        if (executeFastPath(args)) {
            System.exit(0);
        }

        SpringApplication app = new SpringApplication(WebSearchApplication.class);

        // Auto-activate the 'cli' profile for CLI mode.
        // Suppresses logging and uses ephemeral port.
        if (hasCliMode(args)) {
            app.setAdditionalProfiles("cli");
        }

        app.run(args);
    }

    /**
     * Executes fast-path commands (--version, --help, install-skill) without
     * starting the Spring context. Returns true if a command was handled.
     * The caller should return from main() to exit naturally — no Spring,
     * no shutdown hooks, no cleanup, no zombies.
     */
    private static boolean executeFastPath(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "install-skill" -> {
                    InstallSkillCommand.execute(args);
                    return true;
                }
                case "--version", "-V" -> {
                    System.out.println("noetic " + VERSION);
                    return true;
                }
                case "--help", "-h", "help" -> {
                    printHelp(System.out);
                    return true;
                }
                default -> {}
            }
        }
        return false;
    }

    /** Checks if any arg sets CLI mode via the Spring Boot property. */
    private static boolean hasCliMode(String[] args) {
        for (String arg : args) {
            if (arg.contains("websearch.adapter.default-mode=cli")) {
                return true;
            }
        }
        return false;
    }

    /** Checks if any arg is a fast-path command that should run quietly. */
    private static boolean hasFastPathCommand(String[] args) {
        for (String arg : args) {
            if ("install-skill".equals(arg) || "--version".equals(arg)
                    || "-V".equals(arg) || "--help".equals(arg)
                    || "-h".equals(arg) || "help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles fast-path commands as a Spring Boot {@link CommandLineRunner}.
     *
     * <p>In GraalVM native image (AOT) mode, {@code main()} is not reliably
     * called, so pre-Spring fast-path interception does not work. Instead,
     * this runner is a safety net. Primary fast-path handling is in
     * {@link #executeFastPath(String[])} which runs before Spring starts.</p>
     *
     * <p>Runs at highest priority so it executes before any other runners.</p>
     */
    @Component
    @Order(Integer.MIN_VALUE)
    static class FastPathRunner implements CommandLineRunner {
        @Override
        public void run(String... args) {
            if (hasFastPathCommand(args)) {
                executeFastPath(args);
            }
        }
    }

    private static void printHelp(java.io.PrintStream out) {
        out.println("""
                noetic %s - Web search, crawl, and knowledge cache for AI coding assistants.
                
                USAGE:
                  noetic [OPTIONS]                         Start as MCP server (default)
                  noetic [OPTIONS] --websearch.adapter.default-mode=rest
                                                           Start as REST API server
                  noetic [OPTIONS] --websearch.adapter.default-mode=cli <command> [ARGS]
                                                           Run a one-shot CLI command
                  noetic install-skill [ARGS]               Install AI assistant instructions
                
                MODES:
                  mcp (default)    MCP server over STDIO for LLM clients (Cursor, Claude, etc.)
                  rest             REST API with endpoints under /api/v1/
                  cli              One-shot commands, JSON output to stdout
                
                CLI COMMANDS:
                  search <query>          Search the web
                    --max-results=N         Maximum results (default: 10)
                    --freshness=PERIOD      day, week, month, year
                
                  crawl <url>             Fetch and extract page content
                    --fetch-mode=MODE       auto | static | dynamic (default: auto)
                    --include-links         Extract links from the page
                    --include-images        Extract image URLs from the page
                
                  cache <query>           Search the local vector cache
                    --top-k=N               Number of results (default: 5)
                
                  chunk <text>            Split content into chunks and cache
                    --strategy=TYPE         sentence | token | semantic (default: sentence)
                    --max-chunk-size=N      Max tokens per chunk (default: 512)
                
                  sitemap <domain>        Discover URLs from domain sitemap
                    --max-urls=N            Maximum URLs to return (default: 50)
                
                  batch-crawl             Crawl multiple URLs or a domain
                    --domain=DOMAIN         Domain to crawl via sitemap discovery
                    --max-urls=N            Maximum URLs (default: 100)
                
                INSTALL-SKILL:
                  noetic install-skill --list               List supported targets
                  noetic install-skill --target=<target>    Generate instructions file
                
                  Targets: cursor, antigravity, droid, claude-code, openhands,
                           copilot, windsurf, cline, roo, kilo, vibe
                
                COMMON OPTIONS:
                  --server.port=PORT                  Server port (default: 8080)
                  --websearch.fetcher.dynamic.enabled=false
                                                      Disable headless Chromium
                  --websearch.search.active=brave      Switch search provider
                  --websearch.proxy.enabled=true        Enable proxy
                
                EXAMPLES:
                  noetic                                           # MCP server
                  noetic --server.port=8090 \\
                         --websearch.adapter.default-mode=rest     # REST API on :8090
                  noetic --websearch.adapter.default-mode=cli \\
                         search "kubernetes best practices"        # CLI search
                  noetic --websearch.adapter.default-mode=cli \\
                         crawl "https://example.com" \\
                         --fetch-mode=dynamic --include-links      # Dynamic crawl
                  noetic install-skill --target=cursor             # Install for Cursor
                
                More info: https://github.com/dnamaz/noetic
                """.formatted(VERSION));
    }

}
