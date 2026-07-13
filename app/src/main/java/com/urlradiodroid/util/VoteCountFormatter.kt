package com.urlradiodroid.util

import java.util.Locale

/** Formats a Radio Browser station's `votes` count for the small popularity badge in Discover results. */
object VoteCountFormatter {
    fun format(votes: Int): String =
        when {
            votes >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", votes / 1_000_000.0)
            votes >= 1_000 -> String.format(Locale.ROOT, "%.1fk", votes / 1_000.0)
            else -> votes.toString()
        }
}
