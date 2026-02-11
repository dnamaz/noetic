package com.noetic.websearch.model;

/**
 * Recency filter for search results.
 */
public enum Freshness {
    NONE,
    DAY,
    WEEK,
    MONTH,
    YEAR;

    /**
     * Parse a freshness string, returning NONE for null/unknown values.
     */
    public static Freshness parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
