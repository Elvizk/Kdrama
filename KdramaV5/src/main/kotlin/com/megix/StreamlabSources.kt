package com.megix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.withTimeout
import java.util.Locale

// Port de las recetas validadas de StreamLab (`Scraping video/streamlab/sources/`).
// F6 · motchillxf.cc → kvp726: WordPress ophim, HTML plano, sin token y sin Referer.
private const val F6_TAG = "StreamlabF6"
private const val F6_BASE = "https://motchillxf.cc"
private const val F6_BASE_ALT = "https://motchillxv.cc"

private data class F6Match(
    val slug: String,
    val title: String,
    val original: String?,
    var score: Int = 0,
)

/** `.xf` redirige a `.xv` (canónico); se aceptan ambos como base. */
private suspend fun f6Get(path: String): String? {
    for (base in arrayOf(F6_BASE, F6_BASE_ALT)) {
        try {
            val response = app.get(base + path, timeout = 20_000L)
            if (response.code == 200 && response.text.contains("<")) return response.text
        } catch (e: Exception) {
            Log.d(F6_TAG, "GET falló en $base: ${e.message}")
        }
    }
    return null
}

private fun f6Text(fragment: String?): String =
    fragment.orEmpty()
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val f6EpisodeSuffix = Regex(
    """\s*[-–|]\s*(?:tập|episode)\s*\d+\s*$""",
    RegexOption.IGNORE_CASE,
)

private val f6AnchorRegex = Regex(
    """<a[^>]+href="(?:https?://[^"/]+)?/phim/([^"/?#]+)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private val f6OriginalRegex = Regex(
    """class="[^"]*original-title[^"]*"[^>]*>(.*?)</""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private val f6TapRegex = Regex("""/phim/[^"/?#]+/tap-(\d+)-sv-\d+""")

private val f6ServerTagRegex = Regex(
    """<a[^>]*class="[^"]*streaming-server[^"]*"[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val f6DataAttrRegex = Regex("(data-[\\w-]+)=\"([^\"]*)\"")

private fun f6ParseSearch(html: String): List<F6Match> {
    val seen = HashSet<String>()
    val out = mutableListOf<F6Match>()
    for (m in f6AnchorRegex.findAll(html)) {
        val slug = m.groupValues[1]
        if (!seen.add(slug)) continue
        val inner = m.groupValues[2]
        val end = minOf(m.range.last + 1, html.length)
        val window = inner + html.substring(end, minOf(end + 500, html.length))
        val original = f6OriginalRegex.find(window)?.groupValues?.get(1)
            ?.let(::f6Text)?.takeIf { it.isNotEmpty() }
        val title = f6EpisodeSuffix.replace(f6Text(inner), "").trim()
            .ifEmpty { slug.replace("-", " ") }
        out.add(F6Match(slug, title, original))
    }
    return out
}

private fun f6ParseServers(html: String): List<Map<String, String>> =
    f6ServerTagRegex.findAll(html).map { tag ->
        f6DataAttrRegex.findAll(tag.value).associate {
            it.groupValues[1] to it.groupValues[2]
        }
    }.toList()

/**
 * F6: búsqueda por título → ficha → episodio → `data-link` m3u8 del server `sv-0`.
 * Ese server es `kk` (kvp726.com, HLS/TS H.264 1080p) y sirve sin Referer.
 * Los servers `sv-1`/`sv-3` son embeds y `sv-2` es vsmov (F7): se ignoran aquí.
 */
suspend fun CineStreamExtractors.invokeF6(
    title: String?,
    originalTitle: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit,
) {
    val key = normKey(title)
    if (key.isEmpty()) return
    val originalKey = normKey(originalTitle)

    withTimeout(35_000L) {
        // 1) búsqueda: score 100/95/60 según norm_key ( igual que core.py )
        val searchHtml = f6Get("/?s=" + quote(title ?: return@withTimeout)) ?: return@withTimeout
        val ranked = f6ParseSearch(searchHtml)
            .map { m ->
                val t = normKey(m.title)
                val o = normKey(m.original)
                m.score = when {
                    t == key -> 100
                    o == key || (originalKey.isNotEmpty() && o == originalKey) -> 95
                    key.isNotEmpty() && (key in t || key in o || t in key || o in key) -> 60
                    else -> 0
                }
                m
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }

        if (ranked.isEmpty()) return@withTimeout
        val best = ranked.first()
        // Coincidencia débil y ambigua: no adivinar el título.
        if (best.score == 60 && ranked.count { it.score == 60 } > 1) return@withTimeout

        // 2) episodio: el sitio enumera tap-NN; CloudStream aporta T/E directo.
        // Las películas (season == null) usan el episodio 1.
        val ep = if (season == null) 1 else (episode ?: 1).coerceAtLeast(1)
        val detail = f6Get("/phim/${best.slug}") ?: return@withTimeout
        val availableEps = f6TapRegex.findAll(detail)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()
        if (availableEps.isNotEmpty() && ep !in availableEps) return@withTimeout

        // 3) resolución: sv-0 = kk, `data-link` ya es el máster HLS.
        val page = f6Get("/phim/${best.slug}/tap-${ep.toString().padStart(2, '0')}-sv-0")
            ?: return@withTimeout
        val servers = f6ParseServers(page)
        val chosen = servers.firstOrNull { it["data-server"] == "0" } ?: servers.firstOrNull()
            ?: return@withTimeout
        if ((chosen["data-type"] ?: "m3u8").lowercase(Locale.ROOT) != "m3u8") return@withTimeout
        val url = chosen["data-link"]?.takeIf { it.startsWith("http") } ?: return@withTimeout

        Log.d(F6_TAG, "match=${best.score} slug=${best.slug} ep=$ep servers=${servers.size}")
        callback.invoke(
            newExtractorLink(
                "F6 kvp726",
                "F6 kvp726 · sv-${chosen["data-server"] ?: "0"}",
                url,
                INFER_TYPE,
            ) {
                // kvp726 sirve los segmentos sin Referer (verificado en StreamLab).
            }
        )
    }
}
