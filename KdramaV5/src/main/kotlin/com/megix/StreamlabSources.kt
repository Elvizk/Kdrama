package com.megix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

// Port de las recetas validadas de StreamLab (`Scraping video/streamlab/sources/`).
// F6 · motchillxf.cc → kvp726: WordPress ophim, HTML plano, sin token y sin Referer.
private const val F6_TAG = "StreamlabF6"
private const val F6_BASE = "https://motchillxf.cc"
// El sitio redirige a .xh (canónico actual); .xv aparece en la ficha histórica.
private const val F6_BASE_CANONICAL = "https://motchillxh.cc"
private const val F6_BASE_ALT = "https://motchillxv.cc"

private data class LabMatch(
    val slug: String,
    val title: String,
    val original: String?,
    var score: Int = 0,
)

/** `.xf` redirige a `.xv` (canónico); se aceptan ambos como base. */
private suspend fun f6Get(path: String): String? {
    for (base in arrayOf(F6_BASE, F6_BASE_CANONICAL, F6_BASE_ALT)) {
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

private val f6TitleAttrRegex = Regex("title=\"([^\"]*)\"")

private val f6ServerTagRegex = Regex(
    """<a[^>]*class="[^"]*streaming-server[^"]*"[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val f6DataAttrRegex = Regex("(data-[\\w-]+)=\"([^\"]*)\"")

private fun f6ParseSearch(html: String): List<LabMatch> {
    val seen = HashSet<String>()
    val out = mutableListOf<LabMatch>()
    for (m in f6AnchorRegex.findAll(html)) {
        val slug = m.groupValues[1]
        if (!seen.add(slug)) continue
        val inner = m.groupValues[2]
        val end = minOf(m.range.last + 1, html.length)
        val window = inner + html.substring(end, minOf(end + 500, html.length))
        val original = f6OriginalRegex.find(window)?.groupValues?.get(1)
            ?.let(::f6Text)?.takeIf { it.isNotEmpty() }
        // Las tarjetas ponen el título en el atributo `title` del <a> y el
        // interior es sólo una imagen, así que se lee el atributo primero.
        val attrTitle = f6TitleAttrRegex.find(m.value)?.groupValues?.get(1)
        val title = f6EpisodeSuffix.replace(f6Text(attrTitle ?: inner), "").trim()
            .ifEmpty { slug.replace("-", " ") }
        out.add(LabMatch(slug, title, original))
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
        // La ficha NO lista los episodios en HTML (se cargan por JS): no se
        // puede filtrar por ahí. Se construye tap-NN y el 404 del sitio es la
        // señal de "sin cobertura" para ese episodio.

        // 3) resolución: sv-0 = kk, `data-link` ya es el máster HLS.
        val page = f6Get("/phim/${best.slug}/tap-${ep.toString().padStart(2, '0')}-sv-0")
            ?: return@withTimeout
        val servers = f6ParseServers(page)
        val chosen = servers.firstOrNull { it["data-server"] == "0" } ?: servers.firstOrNull()
            ?: return@withTimeout
        if ((chosen["data-type"] ?: "m3u8").lowercase(Locale.ROOT) != "m3u8") return@withTimeout
        val url = chosen["data-link"]?.takeIf { it.startsWith("http") } ?: return@withTimeout

        val serverLabel = chosen["data-source"]?.takeIf { it.isNotBlank() } ?: "kk"
        Log.d(F6_TAG, "match=${best.score} slug=${best.slug} ep=$ep servers=${servers.size}")
        callback.invoke(
            newExtractorLink(
                "F6 - Motchill - $serverLabel - 1080p",
                "F6 - Motchill - $serverLabel - 1080p",
                url,
                INFER_TYPE,
            ) {
                // kvp726 sirve los segmentos sin Referer (verificado en StreamLab).
                // El máster es de una sola rendición 1080p (medido en StreamLab);
                // fijar quality ordena el selector sin peticiones extra.
                quality = Qualities.P1080.value
            }
        )
    }
}

// ── F5 · oppadrama.cc → vid.oppadrama.cc (CDN 97bf1) ─────────────────────
private const val F5_TAG = "StreamlabF5"
private const val F5_EMBED_HOST = "https://vid.oppadrama.cc"

private val f5NavSkip = setOf(
    "the-loai", "quoc-gia", "phim-moi", "phim-bo", "phim-le", "dang-nhap",
    "gioi-thieu", "lien-he", "tag", "category", "page", "search", "wp-login",
    "wp-json", "jadwal", "bookmark", "random", "oppabiz",
)

private val f5AnchorRegex = Regex(
    """<a[^>]+href="(?:https?://[^"/]+)?/([a-z0-9][a-z0-9-]{2,})/?"[^>]*>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private val f5EpisodeSuffix = Regex("""-episode-\d+$""", RegexOption.IGNORE_CASE)
private val f5IframeRegex = Regex("""<iframe[^>]+src="([^"]*/e/[^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)

/** `v2` redirige a `v3` (canónico hoy); se prueban ambos y el dominio raíz. */
private suspend fun f5Get(path: String): String? {
    for (base in arrayOf("https://v3.oppadrama.cc", "https://v2.oppadrama.cc", "https://oppadrama.cc")) {
        try {
            val response = app.get(base + path, timeout = 20_000L)
            if (response.code == 200 && response.text.contains("<")) return response.text
        } catch (e: Exception) {
            Log.d(F5_TAG, "GET falló en $base: ${e.message}")
        }
    }
    return null
}

private fun f5ParseSearch(html: String): List<LabMatch> {
    val seen = HashSet<String>()
    val out = mutableListOf<LabMatch>()
    for (m in f5AnchorRegex.findAll(html)) {
        val slug = f5EpisodeSuffix.replace(m.groupValues[1], "").trim()
        if (slug.isEmpty() || slug in f5NavSkip || !seen.add(slug)) continue
        val title = f6Text(m.groupValues[2]).ifEmpty { slug.replace("-", " ") }
        out.add(LabMatch(slug, title, null))
    }
    return out
}

/**
 * F5: búsqueda WP `?s=` → artículo `{serie}-episode-N` → iframe `/e/{filecode}`
 * → `POST /api/stream` → `streaming_url` HLS/TS H.264 (segmentos `.css`).
 * El token caduca (~1 h): se relee siempre aquí, al resolver el episodio.
 */
suspend fun CineStreamExtractors.invokeF5(
    title: String?,
    originalTitle: String? = null,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val key = normKey(title)
    if (key.isEmpty()) return
    val originalKey = normKey(originalTitle)

    withTimeout(35_000L) {
        // 1) búsqueda
        val searchHtml = f5Get("/?s=" + quote(title ?: return@withTimeout)) ?: return@withTimeout
        val ranked = f5ParseSearch(searchHtml)
            .map { m ->
                val t = normKey(m.title)
                val o = normKey(m.slug.replace("-", " "))
                m.score = when {
                    t == key || o == key -> 95
                    (originalKey.isNotEmpty() && o == originalKey) -> 95
                    key.isNotEmpty() && (key in t || key in o || t in key || o in key) -> 60
                    else -> 0
                }
                m
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
        if (ranked.isEmpty()) return@withTimeout
        val best = ranked.first()
        if (best.score == 60 && ranked.count { it.score == 60 } > 1) return@withTimeout

        // 2) episodio: las películas (season == null) usan el episodio 1
        val ep = if (season == null) 1 else (episode ?: 1).coerceAtLeast(1)
        var article = f5Get("/${best.slug}-episode-$ep/")
        if (article == null) {
            // El artículo puede no existir: se busca el episodes real en la ficha.
            val series = f5Get("/${best.slug}/") ?: return@withTimeout
            val eps = Regex("""${Regex.escape(best.slug)}-episode-(\d+)""")
                .findAll(series).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
            val chosen = if (ep in eps) ep else (eps.minOrNull() ?: 1)
            article = f5Get("/${best.slug}-episode-$chosen/") ?: return@withTimeout
        }

        // 3) iframe → filecode → API (siempre fresca)
        val embed = f5IframeRegex.find(article)?.groupValues?.get(1) ?: return@withTimeout
        val filecode = embed.trimEnd('/').substringAfterLast('/')
        if (filecode.isBlank()) return@withTimeout
        val response = try {
            app.post(
                "$F5_EMBED_HOST/api/stream",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Origin" to F5_EMBED_HOST,
                    "Referer" to "$F5_EMBED_HOST/",
                ),
                json = mapOf(
                    "filecode" to filecode,
                    "device" to "web",
                    "codecs" to listOf("av01.0.09M.10", "hvc1.1.6.L123.B0"),
                ),
            )
        } catch (e: Exception) {
            Log.d(F5_TAG, "POST api/stream falló: ${e.message}")
            return@withTimeout
        }
        if (response.code != 200) return@withTimeout
        val data = try {
            JSONObject(response.text)
        } catch (e: Exception) {
            Log.d(F5_TAG, "api/stream sin JSON: ${e.message}")
            return@withTimeout
        }
        val url = data.optString("streaming_url").ifBlank { data.optString("url") }
            .takeIf { it.startsWith("http") } ?: return@withTimeout

        // Subtítulos opcionales (en la sonda vinieron null)
        data.optJSONArray("subtitles")?.let { subs ->
            for (i in 0 until subs.length()) {
                val s = subs.optJSONObject(i) ?: continue
                val src = s.optString("url").ifBlank { s.optString("src") }
                if (src.isBlank()) continue
                mySubtitleCallback(
                    s.optString("label").ifBlank { data.optString("default_sub_lang") },
                    src,
                    subtitleCallback,
                )
            }
        }

        Log.d(F5_TAG, "match=${best.score} slug=${best.slug} ep=$ep filecode=$filecode")
        callback.invoke(
            newExtractorLink(
                "F5 - OppaDrama - Vidara - Auto (hasta 1080p)",
                "F5 - OppaDrama - Vidara - Auto (hasta 1080p)",
                url,
                INFER_TYPE,
            ) {
                headers = mapOf("Referer" to "$F5_EMBED_HOST/")
                // Máster multi-rendición: ExoPlayer elige; P1080 para ordenar el selector.
                quality = Qualities.P1080.value
            }
        )
    }
}
