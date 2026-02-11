package com.noetic.websearch.adapter.cli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point. Activated when {@code --websearch.adapter.default-mode=cli} is passed.
 * Routes to picocli subcommands for search, crawl, chunk, cache operations.
 *
 * <p>Note: {@code @ConditionalOnProperty} cannot be used here because GraalVM native-image
 * AOT evaluates conditions at build time against default config values. Since the default
 * mode is "mcp", the bean would be permanently excluded from the native binary. Instead,
 * the mode is checked at runtime and the runner is a no-op for non-CLI modes.</p>
 */
@Component
@Order(Integer.MIN_VALUE + 1) // run right after FastPathRunner
public class CliEntryPoint implements CommandLineRunner {

    private final ApplicationContext applicationContext;
    private final WebSearchCommand webSearchCommand;

    @Value("${websearch.adapter.default-mode:mcp}")
    private String mode;

    public CliEntryPoint(ApplicationContext applicationContext, WebSearchCommand webSearchCommand) {
        this.applicationContext = applicationContext;
        this.webSearchCommand = webSearchCommand;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!"cli".equalsIgnoreCase(mode)) {
            return; // not CLI mode — no-op
        }

        CommandLine.IFactory factory = createSpringFactory();
        String[] cliArgs = filterSpringBootArgs(args);
        int exitCode = new CommandLine(webSearchCommand, factory).execute(cliArgs);

        // halt() instead of exit() — skips shutdown hooks entirely.
        // CLI commands are one-shot: output is written, work is done.
        // System.exit() triggers Tomcat graceful shutdown, Lucene close,
        // ONNX cleanup, etc. which can hang and create zombie processes.
        Runtime.getRuntime().halt(exitCode);
    }

    /**
     * Creates a picocli {@link CommandLine.IFactory} that resolves beans from
     * the Spring {@link ApplicationContext}, falling back to picocli's default
     * reflective instantiation for non-Spring types.
     */
    private CommandLine.IFactory createSpringFactory() {
        CommandLine.IFactory defaultFactory = CommandLine.defaultFactory();
        return new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> cls) throws Exception {
                try {
                    return applicationContext.getBean(cls);
                } catch (Exception e) {
                    return defaultFactory.create(cls);
                }
            }
        };
    }

    /**
     * Strips Spring Boot property args (e.g. {@code --server.port=8090},
     * {@code --websearch.adapter.default-mode=cli}) so picocli only sees
     * its own subcommands and options.
     */
    private static String[] filterSpringBootArgs(String[] args) {
        List<String> filtered = new ArrayList<>();
        for (String arg : args) {
            // Spring Boot properties use dotted names: --some.property=value
            if (arg.startsWith("--") && arg.contains(".")) {
                continue; // skip Spring Boot property
            }
            filtered.add(arg);
        }
        return filtered.toArray(String[]::new);
    }
}
