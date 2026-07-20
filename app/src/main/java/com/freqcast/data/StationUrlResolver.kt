package com.freqcast.data

import com.freqcast.util.StreamValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What [StationUrlResolver] found for a pasted homepage URL, ready to be saved as a station. */
data class ResolvedStation(
    val streamUrl: String,
    val isHls: Boolean = false,
    val radioBrowserUuid: String? = null,
)

/**
 * Turns a station's homepage URL (all a non-technical user usually has to paste into
 * [com.freqcast.ui.AddStationScreen]) into a playable stream URL, so "add station" doesn't
 * require already knowing the raw Icecast/Shoutcast mount. Tries, in order:
 * 1. [RadioBrowserApi] - the station may already be cataloged there, keyed off its `homepage`
 *    field, with a known-good stream URL (no scraping needed).
 * 2. The homepage's own HTML - `<audio>`/`<source>` tags, linked `.pls`/`.m3u`/`.m3u8` playlists,
 *    inline JSON player configs, or raw Icecast/Shoutcast/AzuraCast URL conventions.
 * 3. Same-origin `<script>` bundles linked from the page, for sites that render the player
 *    client-side (nothing useful in the initial HTML) - re-scanned with the same patterns as
 *    step 2, plus a probe of any same-domain host they reference for the standard AzuraCast
 *    (`/api/nowplaying`) or Icecast (`/status-json.xsl`) status endpoints.
 *
 * Every URL this produces is still run through [StreamValidator] before being returned, same as
 * a manually typed stream URL - a resolved candidate that turns out unreachable is discarded
 * (or, where more than one candidate exists, skipped in favor of the next).
 */
class StationUrlResolver(
    private val radioBrowserApi: RadioBrowserApi = RadioBrowserApi(),
    private val streamValidator: StreamValidator = StreamValidator(),
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build(),
) {
    suspend fun resolve(homepageUrl: String): ResolvedStation? =
        withContext(Dispatchers.IO) {
            val host = hostOf(homepageUrl) ?: return@withContext null
            fromDirectory(host) ?: fromHtml(homepageUrl, host)
        }

    /** Stage 1: the directory may already carry this station, discoverable via its homepage field. */
    private suspend fun fromDirectory(host: String): ResolvedStation? {
        val keyword = searchKeyword(host) ?: return null
        val candidates =
            try {
                radioBrowserApi.search(keyword, RadioBrowserApi.SearchBy.NAME, limit = 50)
            } catch (e: Exception) {
                return null
            }
        val match = candidates.firstOrNull { hostOf(it.homepage) == host } ?: return null
        if (!streamValidator.isReachable(match.url)) return null
        return ResolvedStation(match.url, match.hls, match.uuid.takeIf(String::isNotBlank))
    }

    /** Stage 2 (+3): scrape the homepage itself, then its linked scripts if that comes up empty. */
    private suspend fun fromHtml(
        homepageUrl: String,
        host: String,
    ): ResolvedStation? {
        val html = fetchText(homepageUrl) ?: return null
        val direct = extractCandidates(html, homepageUrl)
        val candidates = direct.ifEmpty { fromLinkedScripts(html, homepageUrl, host) }
        for (url in rank(candidates, host)) {
            val streamUrl = materialize(url) ?: continue
            if (streamValidator.isReachable(streamUrl)) {
                return ResolvedStation(streamUrl, isHls = streamUrl.contains(".m3u8", ignoreCase = true))
            }
        }
        return null
    }

    /** Follows same-origin `<script src>` bundles - for sites whose player is rendered client-side. */
    private fun fromLinkedScripts(
        html: String,
        baseUrl: String,
        host: String,
    ): List<Candidate> {
        val scriptUrls =
            SCRIPT_SRC_REGEX
                .findAll(html)
                .mapNotNull { resolveAgainst(it.groupValues[1], baseUrl) }
                .filter { url -> url.toHttpUrlOrNull()?.host?.let { it == host || it.endsWith(".$host") } == true }
                .distinct()
                .take(MAX_LINKED_SCRIPTS)
                .toList()

        val candidates = mutableListOf<Candidate>()
        val discoveredOrigins = linkedSetOf<String>()
        val subdomainRegex = Regex("""(https?://[\w-]+\.${Regex.escape(host)})""", RegexOption.IGNORE_CASE)

        for (scriptUrl in scriptUrls) {
            val body = fetchText(scriptUrl) ?: continue
            candidates += extractCandidates(body, scriptUrl)
            subdomainRegex.findAll(body).forEach { discoveredOrigins += it.groupValues[1].lowercase() }
        }

        discoveredOrigins.forEach { origin ->
            panelStreamUrl(origin)?.let { candidates += Candidate(it, origin) }
        }

        return candidates
    }

    /** Well-known status endpoints for the two most common self-hosted radio panels, at [origin] (`scheme://host`). */
    internal fun panelStreamUrl(origin: String): String? = fromAzuraCast(origin) ?: fromIcecast(origin)

    private fun fromAzuraCast(origin: String): String? {
        val body = fetchText("$origin/api/nowplaying") ?: return null
        return try {
            val stations = JSONArray(body)
            if (stations.length() == 0) return null
            stations.getJSONObject(0).optString("listen_url").takeIf(String::isNotBlank)
        } catch (e: Exception) {
            null
        }
    }

    private fun fromIcecast(origin: String): String? {
        val body = fetchText("$origin/status-json.xsl") ?: return null
        return try {
            val source = JSONObject(body).getJSONObject("icestats").opt("source")
            val obj =
                when (source) {
                    is JSONArray -> if (source.length() > 0) source.getJSONObject(0) else return null
                    is JSONObject -> source
                    null -> return null
                    else -> return null
                }
            obj.optString("listenurl").takeIf(String::isNotBlank)
        } catch (e: Exception) {
            null
        }
    }

    /** Resolves a `.pls`/`.m3u` playlist link to the stream URL inside it; passes anything else through. */
    private fun materialize(url: String): String? =
        when {
            url.endsWith(".pls", ignoreCase = true) -> fetchText(url)?.let(::parsePls)
            url.endsWith(".m3u", ignoreCase = true) -> fetchText(url)?.let(::parseM3u)
            else -> url
        }

    private fun parsePls(text: String): String? =
        text
            .lineSequence()
            .firstOrNull { it.trim().startsWith("File1=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.startsWith("http", ignoreCase = true) }

    private fun parseM3u(text: String): String? =
        text
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("http", ignoreCase = true) }

    /** Scans [text] (HTML or JS) for anything that looks like a stream URL, keeping surrounding context for [rank]. */
    internal fun extractCandidates(
        text: String,
        baseUrl: String,
    ): List<Candidate> {
        val unescaped = text.replace("\\/", "/")
        val results = mutableListOf<Candidate>()

        fun collect(
            regex: Regex,
            resolveRelative: Boolean,
        ) {
            regex.findAll(unescaped).forEach { m ->
                val raw = if (m.groupValues.size > 1) m.groupValues[1] else m.value
                val url = if (resolveRelative) resolveAgainst(raw, baseUrl) else raw.takeIf { it.startsWith("http") }
                if (url != null) {
                    val contextStart = maxOf(0, m.range.first - 200)
                    results += Candidate(url, unescaped.substring(contextStart, m.range.first))
                }
            }
        }

        collect(AUDIO_SRC_REGEX, resolveRelative = true)
        collect(STREAM_KEY_REGEX, resolveRelative = false)
        collect(PLAYLIST_REF_REGEX, resolveRelative = true)
        collect(EXTENSION_URL_REGEX, resolveRelative = false)
        collect(PORT_STREAM_REGEX, resolveRelative = false)
        collect(PATH_STREAM_REGEX, resolveRelative = false)

        return results
    }

    /**
     * Orders candidates by how likely they are to be *this* station's stream, for pages whose
     * player config lists a whole network of sibling stations (see [Candidate]'s context window):
     * a candidate hosted on a subdomain of the target site outranks a third-party host, and beyond
     * that a candidate whose nearby text shares a word with the site's own domain label outranks
     * one that doesn't (e.g. "jazz" in both "radiojazzfm.ru" and a nearby `"title":"Jazz FM"`).
     */
    internal fun rank(
        candidates: List<Candidate>,
        host: String,
    ): List<String> {
        val label = searchKeyword(host) ?: return candidates.map { it.url }.distinct()
        return candidates
            .distinctBy { it.url }
            .sortedByDescending { candidate ->
                val urlHost =
                    candidate.url
                        .toHttpUrlOrNull()
                        ?.host
                        ?.lowercase()
                        .orEmpty()
                val hostScore = if (urlHost.contains(label)) 2 else 0
                val contextScore =
                    WORD_REGEX
                        .findAll(candidate.context)
                        .map { it.value.lowercase() }
                        .count { it.length >= 3 && it !in GENERIC_WORDS && label.contains(it) }
                hostScore + contextScore
            }.map { it.url }
    }

    private fun resolveAgainst(
        ref: String,
        baseUrl: String,
    ): String? {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        return base.resolve(ref)?.toString()
    }

    /** The registrable-ish label RadioBrowser station names tend to contain, e.g. "silver" from "silver.ru". */
    internal fun searchKeyword(host: String): String? {
        val labels = host.split(".")
        if (labels.size < 2) return null
        return labels[labels.size - 2].takeIf { it.length >= 3 }
    }

    internal fun hostOf(url: String): String? =
        try {
            val normalized = if (!url.contains("://")) "http://$url" else url
            normalized
                .toHttpUrlOrNull()
                ?.host
                ?.removePrefix("www.")
                ?.lowercase()
        } catch (e: Exception) {
            null
        }

    /**
     * GETs [url] as text, capped at [MAX_FETCH_BYTES] via [okhttp3.Response.peekBody] - a page is
     * never anywhere near that large, but an audio stream mistaken for a homepage (e.g. a
     * directory-search false positive) is unbounded, and peeking rather than reading the full
     * body means this returns instead of hanging on one.
     */
    private fun fetchText(url: String): String? =
        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.peekBody(MAX_FETCH_BYTES).string()
            }
        } catch (e: Exception) {
            null
        }

    /** A stream-URL-shaped match plus the text just before it, used only to disambiguate in [rank]. */
    internal data class Candidate(
        val url: String,
        val context: String,
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (compatible; Freqcast/2.0)"
        private const val MAX_LINKED_SCRIPTS = 8
        private const val MAX_FETCH_BYTES = 2_000_000L

        /** Excluded from [rank]'s context-overlap scoring: generic enough to match almost any radio site's domain. */
        private val GENERIC_WORDS =
            setOf("radio", "live", "stream", "station", "online", "music", "player", "net", "www", "http", "https")
        private val WORD_REGEX = Regex("[A-Za-z]+")
        private val AUDIO_SRC_REGEX =
            Regex("""<(?:audio|source)\b[^>]*\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val SCRIPT_SRC_REGEX = Regex("""<script\b[^>]*\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val PLAYLIST_REF_REGEX =
            Regex("""["']([^"'\s]+\.(?:pls|m3u8?))(?:["'?]|\s)""", RegexOption.IGNORE_CASE)
        private val STREAM_KEY_REGEX =
            Regex(
                """"(?:stream_url|streamUrl|stream|audio_url|audioUrl|mp3Url|radioUrl)"\s*:\s*"(https?:[^"]+)"""",
                RegexOption.IGNORE_CASE,
            )
        private val EXTENSION_URL_REGEX =
            Regex(
                """https?://[\w.-]+(?::\d{2,5})?(?:/[\w\-./%]*)?\.(?:mp3|aac|ogg|opus|m3u8)(?:\?[\w=&%.\-]*)?""",
                RegexOption.IGNORE_CASE,
            )
        private val PORT_STREAM_REGEX =
            Regex("""https?://[\w.-]+:(?:8000|8080|8888|9000)(?:/[\w\-./%;]*)?""", RegexOption.IGNORE_CASE)
        private val PATH_STREAM_REGEX =
            Regex(
                """https?://[\w.-]+(?::\d{2,5})?/(?:stream|listen|live|;stream)(?:\.mp3)?[\w\-./%;]*""",
                RegexOption.IGNORE_CASE,
            )
    }
}
