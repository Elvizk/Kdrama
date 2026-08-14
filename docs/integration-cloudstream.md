# Integración CloudStream — KdramaV5 Provider

Cómo el addon consume la API del backend ListasKdrama (MyDramaList Vanced) y las APIs externas. Documentación espejo de `integration-cloudstream.md` del proyecto ListasKdrama.

---

## Categorías del provider (7, v492)

| Categoría | Fuente | Endpoint/Query |
|-----------|--------|----------------|
| Latest Kdrama | MyDramaList API (backend propio) | `GET https://mydramalist-vanced.onrender.com/api/kdramas/latest?page={p}&per_page=100` |
| Korean Drama - Recent | TMDB discover | `discover/tv?with_original_language=ko&with_genres=18&sort_by=primary_release_date.desc` |
| Korean Drama - Top Rated | TMDB discover | `discover/tv?with_original_language=ko&with_genres=18&sort_by=vote_average.desc&vote_count.gte=200` |
| Chinese Drama - Recent | TMDB discover | `discover/tv?with_original_language=zh&with_genres=18&sort_by=primary_release_date.desc` |
| Chinese Drama - Top Rated | TMDB discover | `discover/tv?with_original_language=zh&with_genres=18&sort_by=vote_average.desc&vote_count.gte=200` |
| Korean Movies | MDbList | `mdblist/an-kah/popular-korean-movies/items/movie` |
| Chinese Movies | MDbList | `mdblist/thedeterminist8/chinese-movies/items/movie` (filtrado por `adult`) |

## Endpoint principal (Latest Kdrama)

```
GET https://mydramalist-vanced.onrender.com/api/kdramas/latest
```

Headers: `x-api-key: <clave>` (campo `mydramaApiKey` del provider).

Respuesta `results[]` (ver `docs/API.md` para el modelo completo). El provider mapea:
- `tmdb_id` → `Data(id = tmdbId, type = "tv")`
- `name` → título
- `poster_path` → `https://image.tmdb.org/t/p/w342{path}`
- `mdl_rating` → `Score.from10(it)`

### Manejo de errores (código del provider)

| Código | Acción |
|--------|--------|
| `errorResp.error == true` | `throw ErrorLoadingException("$msg (retry in ${retry}s)")` |
| JSON inválido | `throw ErrorLoadingException("Invalid MyDramaList response")` |
| HTTP timeout | `app.get(..., timeout = 15000)` → 15s |

## MDbList (Movies)

Ruta en el provider: `request.data` empieza con `mdblist/` → parsea `username/listname/items/mediatype` y llama:
```
https://api.mdblist.com/lists/{username}/{listName}/items/{mediaType}?limit=50&offset={offset}&append_to_response=poster&apikey={key}
```
Paginación por `offset`. Poster incluido en respuesta (`item.poster`).

## Notas importantes

1. `Data(type = "tv")` en Latest Kdrama → TMDB episodes. MDbList `mediaType = "show"` se normaliza a `"tv"` en el bridge (lección 2)
2. `per_page=100` en Latest Kdrama (límite backend máx 100)
3. `poster_path` de TMDB: `https://image.tmdb.org/t/p/w342{path}`
4. Claves: `BuildConfig.TMDB_KEY`, `BuildConfig.MDBLIST_API_KEY`, `mydramaApiKey` (settings) — nunca hardcodeadas
