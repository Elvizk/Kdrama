# API Reference — KdramaV5 (consumo externo)

El addon consume 3 APIs externas. Todas las claves van en `local.properties` → `BuildConfig.*` (nunca hardcodeadas).

---

## 1. MyDramaList API (Latest Kdrama)

Backend propio desplegado en Render (proyecto ListasKdrama): `https://mydramalist-vanced.onrender.com`

### GET /api/kdramas/latest — Catálogo paginado

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| page | int | 1 | Número de página |
| per_page | int | 20 | Resultados por página (máx 100) |

Auth requerida: header `x-api-key: <apikey>` (clave desde `myDramaApiKey` en `CineTmdbProvider`).

Respuesta `results[]`:
```json
{
  "tmdb_id": 123456,
  "name": "The Queen Who Crowns",
  "original_name": "원경",
  "overview": "Sinopsis...",
  "poster_path": "/abc.jpg",
  "first_air_date": "2026-07-20",
  "vote_average": 8.5,
  "episode_run_time": 60,
  "number_of_episodes": 16,
  "mdl_rating": 8.9,
  "tags": ["Romance"],
  "genres": ["Drama"],
  "country": "South Korea",
  "network": "tvN",
  "mdl_slug": "12345-drama-name",
  "source": "mdl"
}
```

Errores: `NO_CACHE` (retry 30s), `429` (rate limit), `503` (sync en progreso).

En código: `MyDramaLatestResponse`/`MyDramaItem` en `CineTmdbProvider.kt` (campo `tmdb_id`, `episode_run_time` como String "1 hr. 10 min.").

---

## 2. TMDB API (discover, search, detail, episodes)

Base: `https://api.themoviedb.org/3` — key `BuildConfig.TMDB_KEY`.

### GET /discover/tv (Korean/Chinese Drama Recent & Top Rated)

| Parámetro | Ejemplo | Nota |
|-----------|---------|------|
| with_origin_country | KR / CN | Origen |
| sort_by | popularity.desc / vote_average.desc | |
| with_original_language | ko / zh | |
| first_air_date.lte | hoy | Evita futuros |

### GET /tv/{id} + /tv/{id}/season/{n} (episodios)

- Detail: `first_air_date`, `number_of_episodes`, `episode_run_time`
- Episodes: lista por temporada

---

## 3. MDbList API (Korean Movies, Chinese Movies)

Base: `https://api.mdblist.com/lists/{username}/{listname}/items/{mediatype}` — key `BuildConfig.MDBLIST_API_KEY` (query param `apikey`).

| Lista | Mediatype | Uso |
|-------|-----------|-----|
| snoak/latest-kdrama-shows | show | Latest Kdrama (legacy) |
| an-kah/popular-korean-movies | movie | Korean Movies |
| thedeterminist8/chinese-movies | movie | Chinese Movies (filtrado por `adult`) |

Paginación: `?limit=50&offset=X&append_to_response=poster`. Rate limit: 1000 req/día (free). Headers: `X-RateLimit-Limit/Remaining/Reset`. HTTP 429 → lista vacía (sin crash).

---

## Notas importantes

1. `mediaType = "show"` de MDbList → normalizar a `"tv"` para TMDB episodes (lección 2)
2. `source = "mdl"` = datos enriquecidos MDL; `source = "tmdb_only"` = solo TMDB
3. `poster_path` usa CDN TMDB: `https://image.tmdb.org/t/p/w500{path}`
4. APIs con campos nullable: usar `Boolean?`/`String?`/`Int?` en data classes (Jackson, gotcha en AGENTS.md)
