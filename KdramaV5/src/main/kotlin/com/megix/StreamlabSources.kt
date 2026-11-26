package com.megix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
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

// ── F3 · framextv.cc (MovieBox MP4/H.264) ──────────────────────────────
private const val F3_TAG = "StreamlabF3"
private const val F3_SITE = "https://framextv.cc"
private val f3Mirrors = arrayOf(
    "https://api.framextv.pro",
    "https://api.framextv.tech",
    "https://api.framextv.xyz",
    "https://api.framextv.cc",
)
private val f3Providers = arrayOf("headhunter", "barbarian_king")
private val f3Headers = mapOf(
    "Accept" to "application/json, text/plain, */*",
    "Origin" to F3_SITE,
    "Referer" to "$F3_SITE/",
)

private data class F3Stream(
    val provider: String,
    val url: String,
    val type: String,
    val quality: String,
)

/** Memo de 60 s para que re-resolver el mismo episodio no reconsulte 8 veces. */
private val f3Cache = object {
    private val ttlMs = 60_000L
    private val data = HashMap<String, Pair<Long, List<F3Stream>>>()
    private val lock = Mutex()

    suspend fun get(key: String): List<F3Stream>? = lock.withLock {
        val hit = data[key] ?: return@withLock null
        if (System.currentTimeMillis() - hit.first > ttlMs) {
            data.remove(key)
            null
        } else {
            hit.second
        }
    }

    suspend fun set(key: String, value: List<F3Stream>) = lock.withLock {
        if (data.size >= 64) data.clear()
        data[key] = System.currentTimeMillis() to value
    }
}

private val f3Mutex = Mutex()

private suspend fun f3Request(
    mirror: String,
    tmdb: Int,
    season: Int?,
    ep: Int,
    provider: String?,
): JSONObject? {
    val path = "/api/stream?type=tv&id=$tmdb&season=${season ?: 1}&episode=$ep" +
        (provider?.let { "&provider=$it" } ?: "")
    return try {
        val response = app.get(mirror + path, headers = f3Headers, timeout = 20_000L)
        if (response.code == 200) JSONObject(response.text) else null
    } catch (e: Exception) {
        Log.d(F3_TAG, "GET falló en ${mirror.removePrefix("https://")}: ${e.message}")
        null
    }
}

private suspend fun f3Collect(tmdb: Int, season: Int?, ep: Int): List<F3Stream> {
    val key = "$tmdb|${season ?: "movie"}|$ep"
    f3Cache.get(key)?.let { return it }
    // single-flight: una sola barrida de mirrors por episodio a la vez
    return f3Mutex.withLock {
        f3Cache.get(key)?.let { return@withLock it }
        val found = mutableListOf<F3Stream>()
        for (provider in f3Providers) {
            for (mirror in f3Mirrors) {
                val data = f3Request(mirror, tmdb, season, ep, provider) ?: continue
                val sources = data.optJSONArray("sources") ?: continue
                var best: F3Stream? = null
                var bestScore = -1
                for (i in 0 until sources.length()) {
                    val s = sources.optJSONObject(i) ?: continue
                    val type = s.optString("type").ifBlank { "mp4" }
                    if (type !in setOf("mp4", "hls", "m3u8")) continue
                    val url = s.optString("url").takeIf { it.startsWith("http") } ?: continue
                    // Máxima calidad, no primer source: el orden del sources[]
                    // lo decide el upstream y puede cambiar (Francesca trae
                    // 480P+360P; hoy el primero ya es el mejor, pero sin
                    // garantía). O(n) en memoria, sin HTTP extra.
                    val score = getIndexQuality(s.optString("quality"))
                    if (best == null || score > bestScore) {
                        bestScore = score
                        best = F3Stream(
                            provider = provider,
                            url = url,
                            type = type,
                            quality = s.optString("quality"),
                        )
                    }
                }
                best?.let { found.add(it) }
                if (found.any { it.provider == provider }) break // mirror sano, siguiente proveedor
            }
        }
        f3Cache.set(key, found)
        Log.d(F3_TAG, "tv/$tmdb/${season ?: 1}/$ep -> ${found.map { it.provider }}")
        found
    }
}

/**
 * F3: `/api/stream?type=tv&id=<tmdb>&season=&episode=&provider=` en 4 mirrors.
 * La `url` devuelta ya pasa por el proxy del agregador (el CDN directo da 429).
 * Los proveedores rotan: se emite un link por cada uno que responda, sin
 * sustituir silenciosamente el pedido (equivalente al 409 de StreamLab).
 */
suspend fun CineStreamExtractors.invokeF3(
    tmdbId: Int?,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit,
) {
    if (tmdbId == null) return
    withTimeout(45_000L) {
        val ep = (episode ?: 1).coerceAtLeast(1)
        val streams = f3Collect(tmdbId, season, ep)
        streams.forEach { s ->
            val qualityLabel = s.quality.ifBlank { if (s.type == "mp4") "MP4" else "HLS" }
                .lowercase(Locale.ROOT)
            val label = "F3 - Framex - ${s.provider} - $qualityLabel"
            val type = if (s.type.equals("mp4", true)) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
            callback.invoke(newExtractorLink(label, label, s.url, type) {
                headers = mapOf("Referer" to "$F3_SITE/")
                quality = getIndexQuality(s.quality)
            })
        }
    }
}

// ── F8 · bingebang.sx ────────────────────────────────────────────────
private const val F8_TAG = "StreamlabF8"
private val f8Bases = arrayOf("https://bingebang.sx", "https://bingebang.st")
private const val F8_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"

private val f8KRegex = Regex("""k\s*=\s*\[([0-9,\s]+)\]""")
private val f8DRegex = Regex("""d\s*=\s*\[([0-9,\s]+)\]""")
private val f8TicketRegex = Regex("""ticket:\s*"([^"]+)"""")
private val f8EncRegex = Regex(""""enc"\s*:\s*"([^"]+)"""")

// Constantes del cifrado (waelum.js): k (7 B) y v (32 B).
private val f8K = intArrayOf(199, 71, 13, 231, 255, 68, 237)
private val f8V = intArrayOf(
    254, 34, 63, 133, 200, 39, 217, 246, 38, 61, 129, 201, 32, 213, 242, 37,
    62, 132, 206, 33, 218, 166, 126, 57, 129, 205, 113, 137, 247, 37, 53, 209,
)

private fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

/** Config XOR de la página play: arrays `k=[...]` y `d=[...]` → texto claro. */
private fun f8DecryptXor(html: String): String? {
    val key = f8KRegex.find(html)?.groupValues?.get(1)
        ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
    val data = f8DRegex.find(html)?.groupValues?.get(1)
        ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
    if (key.isNullOrEmpty() || data.isNullOrEmpty()) return null
    val out = ByteArray(data.size) { i -> (data[i] xor key[i % key.size]).toByte() }
    return out.toString(Charsets.UTF_8)
}

/**
 * Payload ChaCha-like: 16 B IV || ciphertext; keystream = sha256 por bloque.
 * Sin `enc` devuelve el body tal cual (como el `f8.py` original).
 */
private fun f8DecryptPayload(body: String, ticket: String): String {
    val enc = f8EncRegex.find(body)?.groupValues?.get(1) ?: return body
    val padded = enc + "=".repeat((4 - enc.length % 4) % 4)
    val raw = try {
        java.util.Base64.getUrlDecoder().decode(padded)
    } catch (e: Exception) {
        return body
    }
    if (raw.size <= 48) return body
    val payload = raw.copyOfRange(16, raw.size)
    val seed = ByteArray(32) { i -> (f8V[i] xor f8K[i % f8K.size]).toByte() }
    val block = ByteArray(52)
    sha256(seed + ticket.toByteArray(Charsets.UTF_8)).copyInto(block, 0, 0, 32)
    raw.copyInto(block, 32, 0, 16)
    val out = ByteArray(payload.size)
    var counter = 0
    var pos = 0
    while (pos < payload.size) {
        block[48] = (counter shr 24).toByte()
        block[49] = (counter shr 16).toByte()
        block[50] = (counter shr 8).toByte()
        block[51] = counter.toByte()
        val stream = sha256(block.copyOf())
        val n = minOf(32, payload.size - pos)
        for (i in 0 until n) {
            out[pos + i] = (payload[pos + i].toInt() xor stream[i].toInt()).toByte()
        }
        pos += n
        counter++
    }
    return out.toString(Charsets.UTF_8)
}

private data class F8Candidate(val slug: String, val score: Int, val year: Int?)
private data class F8Stream(
    val label: String,
    val url: String,
    val type: ExtractorLinkType,
    val res: String,
    val quality: Int,
)

/** Memo de 60 s por (slug, temporada, episodio); la rotación de servers es alta. */
private val f8Cache = object {
    private val ttlMs = 60_000L
    private val data = HashMap<String, Pair<Long, List<F8Stream>>>()
    private val lock = Mutex()

    suspend fun get(key: String): List<F8Stream>? = lock.withLock {
        val hit = data[key] ?: return@withLock null
        if (System.currentTimeMillis() - hit.first > ttlMs) {
            data.remove(key)
            null
        } else {
            hit.second
        }
    }

    suspend fun set(key: String, value: List<F8Stream>) = lock.withLock {
        if (data.size >= 64) data.clear()
        data[key] = System.currentTimeMillis() to value
    }
}

private val f8Mutex = Mutex()

private suspend fun f8Search(title: String, wantTv: Boolean, year: Int?): F8Candidate? {
    val key = normKey(title)
    if (key.isEmpty()) return null
    for (base in f8Bases) {
        val body = try {
            val response = app.get(
                "$base/api/search/multi?query=${quote(title)}",
                headers = mapOf("User-Agent" to F8_UA, "Accept" to "application/json, text/plain, */*"),
                timeout = 20_000L,
            )
            if (response.code != 200) continue
            response.text
        } catch (e: Exception) {
            continue
        }
        val results = try {
            JSONObject(body).optJSONArray("results")
        } catch (e: Exception) {
            continue
        } ?: continue
        val cands = mutableListOf<F8Candidate>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val playUrl = r.optString("play_url").ifBlank { continue }
            val slug = playUrl.trimEnd('/').substringAfterLast('/').ifBlank { continue }
            val media = r.optString("media_type").ifBlank { "tv" }
            if (wantTv && media != "tv" || !wantTv && media != "movie") continue
            val t = normKey(r.optString("title").ifBlank { r.optString("name") })
            val score = if (t == key) 100 else if (key in t || t in key) 60 else 0
            if (score == 0) continue
            val y = r.optInt("year", -1).takeIf { it > 0 }
            cands.add(F8Candidate(slug, score, y))
        }
        if (cands.isEmpty()) continue
        // Exacto primero; desempate por año ±1 (homónimos, ver ficha §2).
        return cands.sortedWith(
            compareByDescending<F8Candidate> { it.score }
                .thenBy { it.year?.let { y -> year?.let { kotlin.math.abs(y - it) } } ?: Int.MAX_VALUE }
        ).first()
    }
    return null
}

private fun f8Res(quality: String?): Pair<String, Int> {
    val q = quality.orEmpty()
    return when {
        q.contains("4K", ignoreCase = true) -> "2160p" to Qualities.P2160.value
        q.contains("FULL", ignoreCase = true) -> "1080p" to Qualities.P1080.value
        q.trim().equals("HD", ignoreCase = true) -> "720p" to Qualities.P720.value
        else -> {
            val n = getIndexQuality(q)
            if (n == Qualities.Unknown.value) (q.ifBlank { "Auto" }) to Qualities.Unknown.value
            else "${n}p" to n
        }
    }
}

private suspend fun f8Collect(
    slug: String,
    season: Int?,
    ep: Int,
    subtitleCallback: (SubtitleFile) -> Unit,
): List<F8Stream> {
    val cacheKey = "$slug|${season ?: "movie"}|$ep"
    f8Cache.get(cacheKey)?.let { return it }
    return f8Mutex.withLock {
        f8Cache.get(cacheKey)?.let { return@withLock it }
        val out = mutableListOf<F8Stream>()
        for (base in f8Bases) {
            val playUrl = if (season == null) "$base/movie/play/$slug"
            else "$base/tv/play/$slug/${season}/$ep"
            val html = try {
                val response = app.get(
                    playUrl,
                    headers = mapOf("User-Agent" to F8_UA, "Referer" to "$base/"),
                    timeout = 20_000L,
                )
                if (response.code != 200) continue
                response.text
            } catch (e: Exception) {
                continue
            }
            val ticket = (f8DecryptXor(html) ?: continue).let { f8TicketRegex.find(it)?.groupValues?.get(1) }
                ?: continue
            val ph = mapOf(
                "User-Agent" to F8_UA,
                "Accept" to "application/json, text/plain, */*",
                "Referer" to playUrl,
                "Origin" to base,
                "X-BB-Player" to "1",
                "X-BB-Ticket" to ticket,
            )
            val payload = try {
                val response = app.get("$base/api/player/sources", headers = ph, timeout = 20_000L)
                if (response.code != 200) continue
                JSONObject(f8DecryptPayload(response.text, ticket))
            } catch (e: Exception) {
                Log.d(F8_TAG, "sources falló en $base: ${e.message}")
                continue
            }
            val servers = payload.optJSONArray("servers") ?: continue
            for (i in 0 until servers.length()) {
                val sv = servers.optJSONObject(i) ?: continue
                val src = sv.optString("source_url").ifBlank { continue }
                // Un server caído no tumba el inventario: se salta (rotación).
                val resolved = try {
                    val response = app.get(
                        "$base/api/player/resolve?src=${quote(src)}",
                        headers = ph,
                        timeout = 20_000L,
                    )
                    if (response.code != 200) continue
                    JSONObject(f8DecryptPayload(response.text, ticket))
                } catch (e: Exception) {
                    continue
                }
                val url = resolved.optString("url").takeIf { it.startsWith("http") } ?: continue
                val label = sv.optString("label").ifBlank { "srv" }
                val (res, q) = f8Res(sv.optString("quality").ifBlank { resolved.optString("quality") })
                val dup = label.equals("wezen", ignoreCase = true)
                val name = if (dup) "$label (dup F3)" else label
                val type = if (url.substringBefore('?').endsWith(".mp4", ignoreCase = true)) {
                    ExtractorLinkType.VIDEO
                } else {
                    ExtractorLinkType.M3U8
                }
                resolved.optJSONArray("subtitles")?.let { subs ->
                    for (j in 0 until subs.length()) {
                        val s = subs.optJSONObject(j) ?: continue
                        val src = s.optString("url").ifBlank { s.optString("src") }
                        if (src.isBlank()) continue
                        mySubtitleCallback(s.optString("lang").ifBlank { s.optString("label") }, src, subtitleCallback)
                    }
                }
                out.add(F8Stream(name, url, type, res, q))
            }
            if (out.isNotEmpty()) break
        }
        f8Cache.set(cacheKey, out)
        Log.d(F8_TAG, "play $slug s=${season ?: 1} e=$ep -> ${out.map { it.label }}")
        out
    }
}

/**
 * F8: búsqueda por título → play → config XOR (ticket) → `/api/player/sources`
 * → `/api/player/resolve` por server. Cifrado ChaCha-like con SHA-256
 * (port de waelum.js vía `f8.py`). Se emite un link por server; `Wezen` con
 * badge de duplicado F3 (ADR-14). URLs efímeras: siempre frescas, sin caché.
 */
suspend fun CineStreamExtractors.invokeF8(
    title: String?,
    originalTitle: String? = null,
    year: Int? = null,
    airedYear: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    if (title.isNullOrBlank() && originalTitle.isNullOrBlank()) return
    withTimeout(60_000L) {
        val wantTv = season != null
        val refYear = airedYear ?: year
        val cand = f8Search(title ?: return@withTimeout, wantTv, refYear)
            ?: (originalTitle?.takeIf { it != title }?.let { f8Search(it, wantTv, refYear) })
            ?: return@withTimeout
        val ep = (episode ?: 1).coerceAtLeast(1)
        val streams = f8Collect(cand.slug, season, ep, subtitleCallback)
        streams.forEach { s ->
            val label = "F8 - BingeBang - ${s.label} - ${s.res}"
            callback.invoke(newExtractorLink(label, label, s.url, s.type) {
                headers = mapOf("Referer" to "${f8Bases[0]}/", "User-Agent" to F8_UA)
                quality = s.quality
            })
        }
    }
}
