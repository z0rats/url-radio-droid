package com.urlradiodroid.util

/**
 * Converts an ISO 3166-1 alpha-2 country code (e.g. "DE") to its flag emoji via the fixed
 * Unicode regional-indicator-symbol offset — no network call or bundled asset needed.
 */
object CountryFlagEmoji {
    private const val REGIONAL_INDICATOR_OFFSET = 0x1F1E6 - 'A'.code

    fun from(countryCode: String): String? {
        val code = countryCode.trim().uppercase()
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
        return code
            .map { String(Character.toChars(it.code + REGIONAL_INDICATOR_OFFSET)) }
            .joinToString("")
    }
}
