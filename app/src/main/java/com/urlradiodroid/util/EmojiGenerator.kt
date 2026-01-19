package com.urlradiodroid.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap

object EmojiGenerator {
    private val radioEmojis = listOf(
        "📻", "🎵", "🎶", "🎧", "🎤", "🎸", "🎹", "🥁", "🎺", "🎷",
        "🎼", "🎯", "⭐", "🌟", "✨", "💫", "🔥", "💎", "🎪", "🎭"
    )

    private val genreEmojis = mapOf(
        "rock" to "🎸",
        "pop" to "🎤",
        "jazz" to "🎷",
        "classic" to "🎹",
        "classical" to "🎹",
        "electronic" to "🎧",
        "dance" to "💃",
        "hip" to "🎧",
        "rap" to "🎤",
        "country" to "🤠",
        "blues" to "🎸",
        "metal" to "🤘",
        "folk" to "🪕",
        "reggae" to "🎵",
        "latin" to "🌴",
        "news" to "📰",
        "talk" to "💬",
        "sport" to "⚽",
        "sports" to "⚽"
    )

    fun getEmojiForStation(name: String, url: String = ""): String {
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()

        // Check for genre keywords in name
        genreEmojis.forEach { (keyword, emoji) ->
            if (lowerName.contains(keyword) || lowerUrl.contains(keyword)) {
                return emoji
            }
        }

        // Check for specific patterns
        when {
            lowerName.contains("radio") || lowerUrl.contains("radio") -> return "📻"
            lowerName.contains("fm") || lowerName.contains("am") -> return "📡"
            lowerName.contains("music") || lowerUrl.contains("music") -> return "🎵"
            lowerName.contains("live") || lowerUrl.contains("live") -> return "🔴"
            lowerName.contains("news") || lowerUrl.contains("news") -> return "📰"
            lowerName.contains("sport") || lowerUrl.contains("sport") -> return "⚽"
            lowerName.contains("jazz") || lowerUrl.contains("jazz") -> return "🎷"
            lowerName.contains("rock") || lowerUrl.contains("rock") -> return "🎸"
            lowerName.contains("pop") || lowerUrl.contains("pop") -> return "🎤"
            lowerName.contains("classic") || lowerUrl.contains("classic") -> return "🎹"
            lowerName.contains("electronic") || lowerUrl.contains("electronic") -> return "🎧"
        }

        // Generate emoji based on first character hash for consistency
        val hash = (name.hashCode() and Int.MAX_VALUE) % radioEmojis.size
        return radioEmojis[hash]
    }

    fun getEmojiBitmap(emoji: String, size: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.7f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(emoji, x, y, paint)
        return bitmap
    }
}
