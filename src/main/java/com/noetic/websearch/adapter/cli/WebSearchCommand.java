package com.noetic.websearch.adapter.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import org.springframework.stereotype.Component;

/**
 * Top-level picocli command. Subcommands handle individual operations.
 */
@Component
@Command(
        name = "noetic",
        mixinStandardHelpOptions = true,
        version = "noetic",  // actual version resolved at runtime from VERSION
        description = "Noetic - CLI interface for search, crawl, chunk, and cache operations.",
        subcommands = {
                SearchCommand.class,
                CrawlCommand.class,
                SitemapCommand.class,
                BatchCrawlCommand.class,
                ChunkCommand.class,
                CacheCommand.class,
                CachePromoteCommand.class,
                InstallSkillCommand.class
        }
)
public class WebSearchCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
