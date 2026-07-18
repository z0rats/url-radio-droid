package com.urlradiodroid.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** A single station parsed out of an OPML/M3U/PLS playlist file — just enough to insert. */
data class ParsedPlaylistStation(
    val name: String,
    val streamUrl: String,
)

/**
 * Parses OPML, M3U/M3U8, or PLS playlist text into a flat list of stations. Format is sniffed
 * from content, not the source file's name/MIME type — a SAF `content://` URI doesn't reliably
 * carry either. Tried in order: XML containing an `<outline>` tag (OPML), `#EXTM3U`-prefixed text
 * (M3U), `[playlist]`-prefixed text (PLS). Throws [IllegalArgumentException] if none match.
 */
object PlaylistImport {
    fun parse(content: String): List<ParsedPlaylistStation> {
        val trimmed = content.trimStart()
        return when {
            looksLikeOpml(trimmed) -> parseOpml(content)
            trimmed.startsWith("#EXTM3U", ignoreCase = true) -> parseM3u(content)
            looksLikePls(trimmed) -> parsePls(content)
            else -> throw IllegalArgumentException("Unrecognized playlist format")
        }
    }

    private fun looksLikeOpml(trimmed: String): Boolean =
        trimmed.startsWith("<") && trimmed.contains("<outline", ignoreCase = true)

    private fun looksLikePls(trimmed: String): Boolean =
        trimmed
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.equals("[playlist]", ignoreCase = true) == true

    /** Outline nodes can nest (folders/categories) — a flat pass over every START_TAG covers all depths. */
    private fun parseOpml(content: String): List<ParsedPlaylistStation> {
        val stations = mutableListOf<ParsedPlaylistStation>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(content))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val url = parser.attributeIgnoreCase("xmlUrl") ?: parser.attributeIgnoreCase("url")
                val name = parser.attributeIgnoreCase("text") ?: parser.attributeIgnoreCase("title")
                if (!url.isNullOrBlank()) {
                    stations.add(ParsedPlaylistStation(name?.ifBlank { null } ?: url, url))
                }
            }
            eventType = parser.next()
        }
        return stations
    }

    private fun XmlPullParser.attributeIgnoreCase(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).equals(name, ignoreCase = true)) return getAttributeValue(i)
        }
        return null
    }

    /** `#EXTINF:duration,Title` (optional) precedes the URL line it names; bare URL lines get the URL as their name. */
    private fun parseM3u(content: String): List<ParsedPlaylistStation> {
        val stations = mutableListOf<ParsedPlaylistStation>()
        var pendingName: String? = null
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> {
                    Unit
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingName = line.substringAfter(",", missingDelimiterValue = "").trim().ifBlank { null }
                }

                line.startsWith("#") -> {
                    Unit
                }

                else -> {
                    stations.add(ParsedPlaylistStation(pendingName ?: line, line))
                    pendingName = null
                }
            }
        }
        return stations
    }

    private val plsFileKey = Regex("^File(\\d+)$", RegexOption.IGNORE_CASE)
    private val plsTitleKey = Regex("^Title(\\d+)$", RegexOption.IGNORE_CASE)

    private fun parsePls(content: String): List<ParsedPlaylistStation> {
        val files = mutableMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            plsFileKey.matchEntire(key)?.let { files[it.groupValues[1].toInt()] = value }
            plsTitleKey.matchEntire(key)?.let { titles[it.groupValues[1].toInt()] = value }
        }
        return files.toSortedMap().map { (index, url) ->
            ParsedPlaylistStation(titles[index]?.ifBlank { null } ?: url, url)
        }
    }
}
