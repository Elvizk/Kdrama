# Pending Features - Mejoras Pendientes

> Archivo local NO gitignored. Lista de mejoras futuras para KdramaV5.

## Alta Prioridad

### 1. DoramaKun Provider
- **Sitio:** doramakun.evmoh.ru (+ dkun.su, dkun.toxiccat.ru, doramakun.ru)
- **Contenido:** 2,562 K-dramas + 6,481 películas + 10,289 doramas totales
- **Servicios video:** VK Video embeds + Kodik + CDNVideoHub + Collaps + Alloha + VidSeed
- **API:** `/player.php?kp_id={KP_ID}&tmdb_id={TMDB_ID}` → JSON con múltiples CDN
- **Requisito:** Crear extractors nuevos para VK Video embeds y Kodik (no existen en CloudStream)
- **Tags relevantes:** k-drama, k-movie, k-ost, c-drama, j-drama

### 2. Cs-Karma: DoramasLatinoX
- **Sitio:** doramafox.es / doramaslatinox.com
- **Contenido:** Dramas coreanos/chinos/japoneses/tailandeses en español
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
- **Solución:** Agregar `without_keywords=289844|353318` al discover query
- **Keywords:** `289844` = boys' love (bl), `353318` = yaoi
- **Prioridad:** Alta (afecta calidad de resultados)

### 10. Pre-build daemon check + build.ps1 fix
- **Pro

## Media Prioridad

### 5. Cs-Karma: JPFilms
- **Sitio:** jp-films.com
- **Contenido:** Películas/series asiáticas en inglés
- **Requisito:** Copiar provider de Cs-Karma

### 6. CinemaCity Mejoras (de Cs-Karma)
- **Subtítulos:** Parsear `[lang]url` y emitir via subtitleCallback
- **eval(atob):** Decodificar scripts embebidos con eval+atob
- **Recomendaciones:** Scraping de contenido relacionado
- **Requisito:** Copiar 3 features de Cs-Karma a nuestra versión

### 7. Verificar push trigger de CI
- **Status:** GitHub configurado con Allow all actions + Read and write permissions
- **Pendiente:** Hacer push de prueba y verificar que CI se dispara automáticamente
- **Comando verificación:** `gh run list --limit 1 --json event,createdAt` debe mostrar `event: "push"`
- **Si no funciona:** Revisar webhook delivery en repo Settings > Webhooks, o usar `gh workflow run Build --ref master` como fallback

### 8. withTimeout Fix (v478)
- **Archivo:** CineStreamUtils.kt:~562
- **Problema:** runLimitedAsync sin withTimeout → provider lento bloquea pipeline
- **Solución:** Envolver task() con withTimeout(15_000L)
- **Timeouts excesivos:** Bollywood 5min→15s, Stremio 100s→15s, etc.

### 11. Probar `first_air_date.lte=today` literal
- **Problema:** Hoy usamos `SimpleDateFormat` + `Date()` + `Locale` para generar fecha, forzando imports extra
- **Solución:** Verificar si TMDB acepta `first_air_date.lte=today` literalmente
- **Test:** Request manual a discover/tv con `first_air_date.lte=today`
- **Si funciona:** Simplifica código eliminando Date/SimpleDateFormat/Locale

## Baja Prioridad

### 12. codebase-memory-mcp (habilitar cuando salga v0.9.1)
- **Herramienta:** [codebase-memory-mcp](https://github.com/DeusData/codebase-memory-mcp) — MCP server que indexa codebases en knowledge graph persistente
- **Beneficio:** Reduce ~69-87% de tokens por sesión (queries en <1ms vs leer archivos completos)
- **Status:** Binario v0.9.0 instalado en `C:\Users\Elvizk_XML\AppData\Local\Programs\codebase-memory-mcp\`
- **Config MCP:** En `opencode.jsonc`, actualmente `enabled: false`
- **Bloqueado por:** [Issues #1145, #1133, #1165](https://github.com/DeusData/codebase-memory-mcp/issues?q=is%3Aissue+windows+indexing+crash) — worker crashes en Windows en v0.9.0
- **Milestone fix:** `v0.9.1-rc`
- **Para habilitar:** Cambiar `"enabled": false` → `"enabled": true` en `opencode.jsonc`, luego correr `codebase-memory-mcp cli index_repository --project KdramaV5 --path "C:/Users/Elvizk_XML/Documents/Proyecto/Cloudstream/Kdrama/KdramaV5"`
- **Luego de habilitar:** Actualizar AGENTS.md — sección "Architecture" puede ser reemplazada por `get_architecture`

### 9. Cs-Karma: Flixlatam
- **Sitio:** flixlatam.com
- **Contenido:** Doramas + general en español
- **Requisito:** Copiar provider de Cs-Karma

### 10. Cs-Karma: Full4KIzle
- **Sitio:** plusizle.com
- **Contenido:** K-Drama + contenido turco
- **Requisito:** Copiar provider de Cs-Karma

### 11. SwatchSeries.vip
- **Sitio:** swatchseries.vip
- **Contenido:** Películas y series generales
- **Problema:** AJAX encriptado, necesita ejecución JS
- **Requisito:** Investigar viabilidad con WebView renderer

### 12. Proyecto independiente KdramaV5
- **Motivación:** Fork de CSX tiene ~50 extractores/provider no usados. Código muerto en cada sesión.
- **Solución:** Crear repo nuevo solo con módulo KdramaV5 + CloudStream API
- **Ventajas:** Menos tokens, menos complejidad, build más rápido
- **Nota:** En progreso — usuario creará carpeta separada manualmente
