# KdramaV5 - Guia del Repositorio

Archivo COMMITEADO en GitHub. Punto de referencia para opencode y desarrolladores.

---

## Archivos Locales (no en GitHub)

| Archivo | Proposito |
|---------|-----------|
| AGENTS.md | Instrucciones de contexto para opencode. NO ELIMINAR. |
| BUG_LOG.md | Checklist pre-bump + lecciones de errores pasados |
| PENDING_FEATURES.md | Mejoras pendientes priorizadas |
| SYNC_STATE.md | Tracking de upstream sync con SaurabhKaperwan/CSX |
| build.ps1 | Script local para build + backup + deploy |

---

## Branches

| Branch | Proposito | Contenido |
|--------|-----------|-----------|
| master | Codigo fuente | .kt, build.gradle.kts, GUIDE.md |
| builds | Distribucion | KdramaV5.cs3, plugins.json, repo.json |

### .gitignore
Ambos branches comparten el MISMO .gitignore:
Ignoran: build.ps1, AGENTS.md, BUG_LOG.md, PENDING_FEATURES.md, SYNC_STATE.md, backups/, local.properties, .gradle, **/build

---

## Backups
Cada build genera un backup en backups/KdramaV5_v{version}.cs3
El directorio backups/ existe solo localmente (gitignored).

---

## Backup de Archivos Locales

Si los archivos locales se pierden, recrearlos desde los bloques embebidos abajo.

### AGENTS.md

```
# KdramaV5 â€” CloudStream Plugin

## Build & Deploy

- **Build**: `.\build.ps1` (recomendado, muestra progreso + escribe `build.log`) o `./gradlew KdramaV5:make`
- **Build flags**: `.\build.ps1 -NoCopy` para no copiar a backups; `.\build.ps1 -SkipDeploy` para saltar deploy a builds branch
- **Build sin script**: `./gradlew KdramaV5:make --console=plain` (output limpio)
- **Log**: `build.log` en project root â€” cada build agrega timestamp + resultado. Revisar `build.gradle.tmp.log` si hay errores de Gradle. Build.ps1 ya no satura stdout con output de Gradle (lo escribe directo a archivo), solo muestra progreso compacto + resumen final.
- **Version**: `KdramaV5/build.gradle.kts` (`version = X`)
- **Entry point**: `CineStream.kt` registers `CineTmdbProvider()` + all extractors
- **Two branches**: `master` (source) + `builds` (distribution)
- **builds branch**: `KdramaV5.cs3`, `plugins.json`, `repo.json`
- **CI** (`.github/workflows/build.yml`): builds on push to master, force-pushes to `builds`
- **plugins.json** `fileHash` must match the `.cs3` SHA-256; update on every rebuild
- **ALWAYS bump version** before building: `version = X+1` in `build.gradle.kts`. CloudStream auto-detects version changes and re-downloads the plugin without manual reinstall
- **Auto commit+push**: Al terminar cada cambio significativo, hacer commit + push automÃ¡ticamente sin pedir confirmaciÃ³n
- **Pre-bump**: Antes de hacer bump + push, leer `BUG_LOG.md` (checklist + lecciones de errores pasados)
- **build.ps1 auto-deploys**: After build, build.ps1 automatically switches to builds branch, copies .cs3, updates plugins.json, and pushes. CI also deploys on push to master (belt + suspenders)
- **Verify CI triggered**: After `git push origin master`, run `gh run list --limit 1 --json event,createdAt,headSha` â€” should show `push` event with matching `headSha` within 2 min. If CI didn't trigger: `gh workflow run Build --ref master` (manual fallback)
- **gh default repo**: Must be set to `Elvizk/Kdrama` (not upstream `SaurabhKaperwan/CSX`). Verify with `gh repo set-default --view`. If unset or wrong: `gh repo set-default Elvizk/Kdrama`. Push trigger is broken on our fork â€” always trigger CI manually after push.

## CI Monitoring

- **Nunca usar `gh run watch`** â€” genera output masivo cada 3s que satura el contexto
- **En su lugar:** esperar ~3 min tras push, luego consultar una sola vez: `gh run view <id> --json status,conclusion`
- **Trigger manual (fallback):** `gh workflow run Build --ref master` â†’ imprime URL del run
- **Verificar resultado:** `gh run view <id> --json conclusion` â†’ `"success"` o `"failure"`
- **Root cause del bug v478**: El push trigger de CI nunca funcionÃ³ en este fork (todos los runs eran `workflow_dispatch`). Siempre verificar que CI se activÃ³ con `gh run list --limit 1` tras cada push a master

## Local Build Setup

- `local.properties` (gitignored) required:
  ```
  sdk.dir=C:/Users/Elvizk_XML/AppData/Local/Android/Sdk
  TMDB_KEY=<from_github_secrets>
  TRAKT_CLIENT_ID=<from_github_secrets>
  MDBLIST_API_KEY=<from mdblist.com/preferences/#api>
  ```
- **NOT** deleted by git checkout (it's gitignored). **NEVER manually delete** `local.properties`. It contains secrets (TMDB_KEY, TRAKT_CLIENT_ID, MDBLIST_API_KEY) â€” if deleted, must ask user for them again
- Windows: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"`
- **Gradle Daemon cold start**: Primera vez despuÃ©s de un rato: ~15min (daemon + dependencies + compilation). Builds subsecuentes con daemon warm: ~2-5min. La optimizaciÃ³n de `gradle.properties` (parallel, config-cache, -Xmx4096m) reduce tiempos. `build.ps1` ya incluye un check automÃ¡tico: si el daemon estÃ¡ frÃ­o, muestra advertencia antes de empezar.
- **gradle.properties optimizations** (v479+): `-Xmx4096m` (2GBâ†’4GB), `parallel=true`, `configuration-cache=true`, `enableJetifier=false`

## Architecture

- **One provider**: `CineTmdbProvider` (`name = "Kdrama TMDB"`)
- **Categories**: `mainPageOf(...)` in `CineTmdbProvider.kt`
  - MyDramaList API (1): "Latest Kdrama" â€” custom backend at mydramalist-vanced.onrender.com
  - TMDB-based (4): Korean Drama Recent/Top Rated, Chinese Drama Recent/Top Rated
  - MDbList-based (2): "Korean Movies" (an-kah), "Chinese Movies" (thedeterminist8)
- **MDbList**: used for 3 curated lists (replaced Trakt in v478)
  - `"mdblist/snoak/latest-kdrama-shows/items/show"` â†’ "Latest Kdrama"
  - `"mdblist/an-kah/popular-korean-movies/items/movie"` â†’ "Korean Movies"
  - `"mdblist/thedeterminist8/chinese-movies/items/movie"` â†’ "Chinese Movies" (filtered)
  - Base URL: `https://api.mdblist.com/lists/{username}/{listname}/items/{mediatype}`
  - Auth: `apikey` query parameter (`BuildConfig.MDBLIST_API_KEY`)
  - Pagination: `?limit=50&offset=X&append_to_response=poster` â€” single request returns items + poster URLs
  - Rate limit: 1,000 req/day (Free tier). Check `X-RateLimit-Remaining` header. HTTP 429 handled gracefully (returns empty list, no crash)
  - Rate limit headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
  - MDbList items include `adult` field (Int: 0 or 1) used for Chinese Movies filter
- **Trakt**: still available via `BuildConfig.TRAKT_CLIENT_ID` but no longer used for home page lists
- **TMDB**: used for discover, search, detail, episodes, recommendations
- **API keys**: `BuildConfig.TMDB_KEY`, `BuildConfig.TRAKT_CLIENT_ID`, `BuildConfig.MDBLIST_API_KEY` via `buildConfigField`; never hardcoded
- **JSON parsing**: use `tryParseJson<T>()` (Jackson `@JsonProperty`); NOT `parsed<T>()`
- **Data classes**: `@param:JsonProperty("name")` annotations

## Current Categories (v489)

7: Latest Kdrama (MyDramaList API), Korean Drama - Recent/Top Rated, Chinese Drama - Recent/Top Rated, Korean Movies (MDbList), Chinese Movies (MDbList, filtered)

## Gotchas

- `.gitignore` with `**/build` must exist on `builds` branch or build artifacts get committed
- CI uses `git commit --amend` + `--force` on `builds` â€” exactly one commit
- `TvType.Anime` and `TvType.Torrent` intentionally excluded
- Empty TMDB_KEY â†’ silent API failure
- **Jackson**: nullable fields in data classes must use `Boolean?` not `Boolean` to handle `"field": null` from JSON APIs (CloudStream pre-release changed null handling)
- **Siempre consultar antes de implementar**: Antes de cualquier cambio en el cÃ³digo, pasar a modo plan y presentar el plan al usuario. No implementar sin aprobaciÃ³n explÃ­cita. Si se estÃ¡ en modo build, indicar "cambiemos a modo plan" antes de proceder.
- Para upstream sync: existe `SYNC_STATE.md` (local) con tracking del ultimo SHA sincronizado y decisiones de providers
- Para mejoras futuras: existe `PENDING_FEATURES.md` (local) con lista de features pendientes

## Local Backups

- `backups/` directory at project root stores a copy of every build `.cs3`:
  ```
  backups/KdramaV5_v471.cs3
  backups/KdramaV5_v472.cs3
  backups/KdramaV5_v473.cs3
  backups/KdramaV5_v474.cs3
  backups/KdramaV5_v475.cs3
  backups/KdramaV5_v476.cs3
  backups/KdramaV5_v477.cs3
  backups/KdramaV5_v478.cs3
  ```
- Auto-copy on every build: `Copy-Item build/KdramaV5.cs3 backups/KdramaV5_v<version>.cs3`
- `backups/` is in `.gitignore` â€” never pushed to GitHub

## Content Filters

- **Chinese Movies (v476+):** Filtro de contenido adulto activo usando MDbList `adult` field
  - Detecta contenido +18 mediante el campo `adult` (Int: 0 o 1) del API de MDbList
  - Aplica solo a la lista `thedeterminist8/chinese-movies`
  - Nota: Antes usaba TMDB `adult` flag (Boolean) â€” migrado a MDbList `adult` field (Int) en v478

## Pendiente: Fix "Skip loading(10)" (futuro)

- `runLimitedAsync` en `CineStreamUtils.kt:~562` no tiene `withTimeout` â†’ un provider lento bloquea todo el pipeline porque `awaitAll()` espera a que TODOS terminen
- **SoluciÃ³n:** envolver `task()` con `withTimeout(15_000L)` dentro de `runLimitedAsync`
- **Complemento:** reducir timeouts excesivos en `CineStreamExtractors.kt`:
  - `invokeBollywood` ~3241: `300000` (5 min) â†’ `15000` (15s)
  - Stremio globals ~3459/3519/3550: `100000L` (100s) â†’ `15000L` (15s)
  - `invokeStremioStreams` ~819: `50000L` (50s) â†’ `15000L` (15s)
  - `invokeDahmerMovies` ~866: `60L` (60s) â†’ `15L` (15s)
- **Importante:** timeout es por provider individual, no por todo el proceso de bÃºsqueda. Providers rÃ¡pidos no se ven afectados
- **Retomar:** cuando el usuario indique que el stuck sigue siendo problema

## Bug Fix v477 (documentado)

- **Error:** `invokeCtgMovies` en `CineStreamExtractors.kt:5212` fallÃ³ con "Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'"
- **Causa:** `val playUrl = link.hlsUrl ?: link.url` es `String?` (ambos campos son nullable en `CtgLink`), pero se usÃ³ sin null check
- **Fix:** Cambiar a `val playUrl = link.hlsUrl ?: link.url ?: return@forEach` y `"CTGMovies ${link.source ?: "default"}"` para manejar nulls
- **LecciÃ³n:** Al copiar cÃ³digo del upstream, siempre verificar nullability de campos en data classes nuevas

## Pendiente: MigraciÃ³n a repo privado (standby)

- Crear repo nuevo `Elvizk/KdramaV5` como privado independiente
- Push de `master` y `builds`
- Cambiar referencias a `SaurabhKaperwan/CSX` por `Elvizk/KdramaV5` en `build.gradle.kts` y `KdramaV5/build.gradle.kts`
- Configurar secrets del nuevo repo
- Verificar CI y probar antes de eliminar el fork viejo
- **Retomar:** cuando el usuario lo indique

## Historial de Cambios

- **v489 (2026-07-27):** Fix MyDramaItem: campo tmdb_id en vez de id, episode_run_time como String (API devuelve '1 hr. 10 min.')
- **v488 (2026-07-27):** Latest Kdrama ahora usa API personalizada (mydramalist-vanced.onrender.com) en vez de TMDB discover. Agrego mydramaAPI, mydramaApiKey, data classes MyDramaLatestResponse/MyDramaItem, manejo de NO_CACHE.
- **v487 (2026-07-18):** Filtro de numero de episodios: excluye dramas con 1-2 episodios.
- **v486 (2026-07-18):** Agregado filtro first_air_date.lte=today a Latest Kdrama.
- **v485 (2026-07-18):** Rollback de sort params + eliminado date filter de Latest Kdrama.
- **v484 (2026-07-18):** Agregado overview fallback filter para short dramas sin metadata.
- **v483 (2026-07-18):** Reemplazado runtime filter con parallel TMDB detail fetch (3 requests simultaneos por drama).
- **v482 (2026-07-18):** Agregado with_runtime.gte=30 filter a Latest Kdrama.
- **v481 (2026-07-18):** Reemplazado Latest Kdrama MDbList con TMDB discover + fix sort params.
- **v480 (2026-07-18):** Fix MDbList type 'show' a 'tv' para TMDB episode loading.
- **v479 (2026-07-14):** Sync upstream 0a63c497: Vivibebe, CF bypass per-domain, cfGet a app.get cleanup.
- **v478 (2026-07-18):** Migracion Trakt a MDbList API para las 3 listas curadas. 1 request por pagina (vs 51 con Trakt), posters incluidos, rate limit validation con HTTP 429. Added MDbList API key field.
- **v477 (2026-07-14):** Sync upstream: +Vidup y CtgMovies providers, -16 providers anime puros
- **v476 (2026-07-10):** Adult content filter for Chinese Movies (TMDB adult flag)
- **v475 (2026-07-10):** Chinese Movies cambiado de TMDB discover a Trakt list (thedeterminist8/chinese-movies)
- **v474 (2026-07-10):** Korean Movies cambiado de TMDB discover a Trakt list (an-kah/popular-korean-movies)


```

### BUG_LOG.md

```
# Bug Log ÔÇö Lecciones Aprendidas

> Archivo local NO gitignored. Se lee solo al preparar una nueva versi├│n (ver AGENTS.md: Pre-bump).

## Checklist Pre-bump

Antes de hacer bump + push, verificar cada punto:

- [ ] **plugins.json es arreglo** ÔÇö `git show origin/builds:plugins.json | Select -First 1` debe empezar con `[`
- [ ] **Tipos bridge normalizados** ÔÇö Si conectas 2 APIs, los tipos coinciden (ej: MDbList `"show"` ÔåÆ TMDB `"tv"`). Buscar en c├│digo: `data.type`, `mediaType`, `type =` en la ruta del bridge
- [ ] **build.ps1 output completo** ÔÇö Ejecutar y verificar que el resumen final incluya: BUILD SUCCESS, Output, Backup, Pushed
- [ ] **builds branch actualizado** ÔÇö `git fetch origin builds && git log origin/builds --oneline -1`
- [ ] **CI trigger** ÔÇö `gh run list --repo Elvizk/Kdrama --limit 1 --json event` debe mostrar `"push"`. Si no, trigger manual: `gh workflow run Build --ref master`
- [ ] **Probar en Cloudstream** ÔÇö Pull-to-refresh, probar cada categor├¡a, verificar episodios carguen

---

## Lecciones por Error

### 1. plugins.json debe ser arreglo, no objeto
- **S├¡ntoma:** Cloudstream no detecta el plugin tras deploy
- **Causa:** `ConvertFrom-Json` en PS5.1 desenvuelve arreglos de 1 elemento; `ConvertTo-Json` serializa como `{...}`
- **Fix:** Forzar wrapping manual: `if ($json -notmatch '^\[') { $json = "[$json]" }`
- **Regla:** Verificar formato del JSON remoto post-deploy

### 2. Normalizar tipos entre APIs
- **S├¡ntoma:** "Coming soon" en episodios solo en categor├¡as MDbList shows
- **Causa:** MDbList devuelve `mediaType = "show"`, TMDB espera `"tv"` en `/tv/{id}/season/...`
- **Fix:** `val normalizedType = if (mediaType == "movie") "movie" else "tv"`
- **Regla:** Todo valor crudo de API externa usado como par├ímetro de otra API debe normalizarse expl├¡citamente

### 3. Output post-build invisible
- **S├¡ntoma:** Build exitoso pero tool parece "colgado" ÔÇö no muestra status, backup ni deploy
- **Causa:** Gradle output masivo satura stdout del tool; `Write-Host` no va a stdout
- **Fix:** Gradle escribe a `build.gradle.tmp.log`; solo dots de progreso en stdout; `Write-Output` en `Log`
- **Regla:** Scripts que lanzan procesos con output masivo deben log a archivo, no saturar stdout

### 4. Push trigger de CI no funciona en forks
- **S├¡ntoma:** Pushes a master no disparan CI autom├ítico
- **Causa:** GitHub Actions en forks requiere Settings > Actions > General > "Allow all actions"
- **Workaround:** `gh workflow run Build --ref master` manual tras cada push
- **Regla:** Verificar CI tras primer push de la sesi├│n. Si no es `"push"`, trigger manual
- **Nota 2026-07-27:** Adem├ís, `gh` apuntaba al upstream en lugar del fork (ver lecci├│n 7)

### 5. Deploy manual vs autom├ítico a builds branch
- **S├¡ntoma:** C├│digo nuevo en master pero Cloudstream no lo refleja
- **Causa:** Cloudstream lee del branch `builds`, no de `master`
- **Fix:** `build.ps1` auto-deploy. CI tambi├®n deploya en `workflow_dispatch`.
- **Regla:** Siempre verificar ambos branches: `git log origin/master --oneline -1` + `git log origin/builds --oneline -1`

### 6. Cloudstream cachea datos entre versiones
- **S├¡ntoma:** Fix aplicado pero usuario ve comportamiento viejo
- **Causa:** Cloudstream cachea search responses + show data localmente
- **Workaround:** Pull-to-refresh en categor├¡a; forzar detenci├│n de app; reinstalar plugin si persiste
- **Regla:** Tras cambiar formato de datos, informar al usuario que debe refrescar

### 7. `gh` CLI sin default repo apunta al upstream
- **S├¡ntoma:** `gh run list` mostraba runs de `SaurabhKaperwan/CSX`; `gh workflow run` daba 403
- **Causa:** `gh` no ten├¡a default repo configurado, infer├¡a el primer remote (`upstream`)
- **Fix:** `gh repo set-default Elvizk/Kdrama`
- **Regla:** Al inicio de cada sesi├│n, verificar `gh repo set-default --view`

### 8. Gradle daemon cold start sin feedback visual
- **S├¡ntoma:** Tool parece "pensando indefinidamente" en primer build del d├¡a
- **Causa:** Daemon cold start tarda ~15min sin output apreciable (cache config, compilation)
- **Fix:** Antes del primer build del d├¡a, verificar daemon con `./gradlew --status`. Si est├í fr├¡o, informar al usuario: "Cold start ~15min"
- **Regla:** No asumir build colgado; verificar daemon primero
```

### PENDING_FEATURES.md

```
# Pending Features - Mejoras Pendientes

> Archivo local NO gitignored. Lista de mejoras futuras para KdramaV5.

## Alta Prioridad

### 1. DoramaKun Provider
- **Sitio:** doramakun.evmoh.ru (+ dkun.su, dkun.toxiccat.ru, doramakun.ru)
- **Contenido:** 2,562 K-dramas + 6,481 pel├¡culas + 10,289 doramas totales
- **Servicios video:** VK Video embeds + Kodik + CDNVideoHub + Collaps + Alloha + VidSeed
- **API:** `/player.php?kp_id={KP_ID}&tmdb_id={TMDB_ID}` ÔåÆ JSON con m├║ltiples CDN
- **Requisito:** Crear extractors nuevos para VK Video embeds y Kodik (no existen en CloudStream)
- **Tags relevantes:** k-drama, k-movie, k-ost, c-drama, j-drama

### 2. Cs-Karma: DoramasLatinoX
- **Sitio:** doramafox.es / doramaslatinox.com
- **Contenido:** Dramas coreanos/chinos/japoneses/tailandeses en espa├▒ol
- **Servicios:** Custom AES+SHA256 decryption extractor
- **Requisito:** Copiar provider completo de Cs-Karma

### 3. Cs-Karma: DramaFlix
- **Sitio:** dramaflix.cc
- **Contenido:** Short dramas (ShortMax, DramaBox, ReelShort) en turco
- **Servicios:** API con CDN ticket auth
- **Requisito:** Copiar provider completo de Cs-Karma

### 4. TVids.to Provider

### 9. Excluir BL dramas (Boy Love)
- **Problema:** Dramas BL aparecen en Latest Kdrama
- **Soluci├│n:** Agregar `without_keywords=289844|353318` al discover query
- **Keywords:** `289844` = boys' love (bl), `353318` = yaoi
- **Prioridad:** Alta (afecta calidad de resultados)

### 10. Pre-build daemon check + build.ps1 fix
- **Pro

## Media Prioridad

### 5. Cs-Karma: JPFilms
- **Sitio:** jp-films.com
- **Contenido:** Pel├¡culas/series asi├íticas en ingl├®s
- **Requisito:** Copiar provider de Cs-Karma

### 6. CinemaCity Mejoras (de Cs-Karma)
- **Subt├¡tulos:** Parsear `[lang]url` y emitir via subtitleCallback
- **eval(atob):** Decodificar scripts embebidos con eval+atob
- **Recomendaciones:** Scraping de contenido relacionado
- **Requisito:** Copiar 3 features de Cs-Karma a nuestra versi├│n

### 7. Verificar push trigger de CI
- **Status:** GitHub configurado con Allow all actions + Read and write permissions
- **Pendiente:** Hacer push de prueba y verificar que CI se dispara autom├íticamente
- **Comando verificaci├│n:** `gh run list --limit 1 --json event,createdAt` debe mostrar `event: "push"`
- **Si no funciona:** Revisar webhook delivery en repo Settings > Webhooks, o usar `gh workflow run Build --ref master` como fallback

### 8. withTimeout Fix (v478)
- **Archivo:** CineStreamUtils.kt:~562
- **Problema:** runLimitedAsync sin withTimeout ÔåÆ provider lento bloquea pipeline
- **Soluci├│n:** Envolver task() con withTimeout(15_000L)
- **Timeouts excesivos:** Bollywood 5minÔåÆ15s, Stremio 100sÔåÆ15s, etc.

### 11. Probar `first_air_date.lte=today` literal
- **Problema:** Hoy usamos `SimpleDateFormat` + `Date()` + `Locale` para generar fecha, forzando imports extra
- **Soluci├│n:** Verificar si TMDB acepta `first_air_date.lte=today` literalmente
- **Test:** Request manual a discover/tv con `first_air_date.lte=today`
- **Si funciona:** Simplifica c├│digo eliminando Date/SimpleDateFormat/Locale

## Baja Prioridad

### 12. codebase-memory-mcp (habilitar cuando salga v0.9.1)
- **Herramienta:** [codebase-memory-mcp](https://github.com/DeusData/codebase-memory-mcp) ÔÇö MCP server que indexa codebases en knowledge graph persistente
- **Beneficio:** Reduce ~69-87% de tokens por sesi├│n (queries en <1ms vs leer archivos completos)
- **Status:** Binario v0.9.0 instalado en `C:\Users\Elvizk_XML\AppData\Local\Programs\codebase-memory-mcp\`
- **Config MCP:** En `opencode.jsonc`, actualmente `enabled: false`
- **Bloqueado por:** [Issues #1145, #1133, #1165](https://github.com/DeusData/codebase-memory-mcp/issues?q=is%3Aissue+windows+indexing+crash) ÔÇö worker crashes en Windows en v0.9.0
- **Milestone fix:** `v0.9.1-rc`
- **Para habilitar:** Cambiar `"enabled": false` ÔåÆ `"enabled": true` en `opencode.jsonc`, luego correr `codebase-memory-mcp cli index_repository --project KdramaV5 --path "C:/Users/Elvizk_XML/Documents/Proyecto/Cloudstream/Kdrama/KdramaV5"`
- **Luego de habilitar:** Actualizar AGENTS.md ÔÇö secci├│n "Architecture" puede ser reemplazada por `get_architecture`

### 9. Cs-Karma: Flixlatam
- **Sitio:** flixlatam.com
- **Contenido:** Doramas + general en espa├▒ol
- **Requisito:** Copiar provider de Cs-Karma

### 10. Cs-Karma: Full4KIzle
- **Sitio:** plusizle.com
- **Contenido:** K-Drama + contenido turco
- **Requisito:** Copiar provider de Cs-Karma

### 11. SwatchSeries.vip
- **Sitio:** swatchseries.vip
- **Contenido:** Pel├¡culas y series generales
- **Problema:** AJAX encriptado, necesita ejecuci├│n JS
- **Requisito:** Investigar viabilidad con WebView renderer

### 12. Proyecto independiente KdramaV5
- **Motivaci├│n:** Fork de CSX tiene ~50 extractores/provider no usados. C├│digo muerto en cada sesi├│n.
- **Soluci├│n:** Crear repo nuevo solo con m├│dulo KdramaV5 + CloudStream API
- **Ventajas:** Menos tokens, menos complejidad, build m├ís r├ípido
- **Nota:** En progreso ÔÇö usuario crear├í carpeta separada manualmente
```

### SYNC_STATE.md

```
# Sync State - Upstream Tracking

> **Archivo local NO gitignored.** Contiene el estado del tracking con upstream (SaurabhKaperwan/CSX).

## ├Ültimo Sync

- **Upstream repo:** SaurabhKaperwan/CSX
- **├Ültimo SHA sincronizado:** 0a63c497 (minor fix)
- **Fecha:** 2026-07-19
- **Versi├│n local:** v478
- **Repo local:** Elvizk/Kdrama
- **Aplicado:** Vivibebe extractor, CF bypass per-domain (ConcurrentHashMap), cfGetÔåÆapp.get en Bollyflix/Uhdmovies/Moviesmod, Lordflix conservado (URL snowhouse activa)

## Workflow de Sync (pasos)

1. Ver commits nuevos desde ├║ltimo SHA:
   ```
   gh api "repos/SaurabhKaperwan/CSX/commits?per_page=15" --jq '.[].commit.message'
   ```
2. Leer diff de cada commit nuevo (excluir los ya evaluados en tabla abajo)
3. Consultar tabla de providers ÔåÆ saber qu├® ya fue evaluado
4. Aplicar decisiones del usuario (secci├│n de abajo)
5. Build local, commit, push
6. **Actualizar este archivo** con nuevo SHA y versi├│n

## Provider Registry (evaluado)

| Provider | Upstream commit | Local state | Raz├│n |
|---|---|---|---|
| Cloudflare bypass | 7e0708c | Ô£à Ya presente | Implementado localmente antes del sync |
| VaPlayer | d7339db | Ô£à Incluido | Ya exist├¡a localmente |
| HdGharTv | b9cf1ee | Ô£à Incluido | Ya exist├¡a localmente |
| Anikoto | b9cf1ee | ÔØî Excluido | Provider anime puro |
| Castle | f9465f1 | ÔØî Excluido | Contenido indio solamente |
| CtgMovies | 4d26499 | Ô£à Incluido | Tiene dramas coreanos/chinos |
| Movieblast | e9a50a3 | ÔØî Excluido | Mayormente contenido indio (Bollywood/Telugu) |
| Vidup | 8707428 | Ô£à Incluido | Contenido general, multi-servidor |

## Provider Exclusions (permanente)

- **Castle:** Solo contenido indio ÔÇö usuario confirm├│ excluir
- **Movieblast:** Bollywood/Telugu ÔÇö usuario confirm├│ excluir
- **Anikoto:** Provider anime puro ÔÇö usuario decidi├│ excluir providers anime puros
- **Otros anime puros:** (ver v477 en Historial) 16 providers eliminados en v477

## User Decisions Log

- **v477:** "include all 1080p EXCEPT Indian/anime" ÔÇö focus on Korean/Chinese dramas
- **v477:** Cloudflare bypass ÔåÆ ya presente, sin acci├│n necesaria
- **v477:** withTimeout fix ÔåÆ diferido a v478
- **v477:** Castle omitido (solo indio)
- **v477:** Movieblast omitido (mayormente indio)

## Pending from Upstream

(Ninguno ÔÇö sincronizado completamente hasta 8707428)
```
