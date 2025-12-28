package com.emailclient.util

/**
 * Utility for sanitizing search queries to prevent SQL injection via LIKE clauses.
 */
object SearchQuerySanitizer {

    private const val MAX_QUERY_LENGTH = 100

    /**
     * Sanitizes a search query by:
     * 1. Escaping LIKE special characters (%, _, \)
     * 2. Limiting the query length
     * 3. Trimming whitespace
     *
     * This prevents SQL injection attacks via malicious LIKE patterns.
     */
    fun sanitize(query: String): String {
        return query
            .trim()
            .take(MAX_QUERY_LENGTH)
            .replace("\\", "\\\\")  // Escape backslash first
            .replace("%", "\\%")     // Escape percent
            .replace("_", "\\_")     // Escape underscore
    }
}
