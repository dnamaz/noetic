package com.dnamaz.websearch.model;

/**
 * Declares what a ContentFetcher implementation supports.
 */
public record FetcherCapabilities(
        boolean supportsJavaScript,
        boolean supportsWaitForSelector,
        boolean supportsMarkdownOutput,
        boolean supportsBotBypass,
        boolean supportsProxy,
        boolean supportsScreenshot,
        boolean requiresApiKey,
        boolean requiresLocalBinary
) {}
