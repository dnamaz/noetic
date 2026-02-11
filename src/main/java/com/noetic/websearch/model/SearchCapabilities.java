package com.noetic.websearch.model;

/**
 * Declares what a SearchProvider supports.
 * Used by the service layer to degrade gracefully when filters are unsupported.
 */
public record SearchCapabilities(
        boolean supportsFreshness,
        boolean supportsLanguage,
        boolean supportsCountry,
        boolean supportsDomainFiltering,
        boolean supportsRawContent,
        boolean supportsAiAnswer,
        int maxResultsLimit
) {}
