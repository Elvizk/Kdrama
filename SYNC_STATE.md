# Sync State - Upstream Tracking

> **Archivo local NO gitignored.** Contiene el estado del tracking con upstream (SaurabhKaperwan/CSX).

## Último Sync

- **Upstream repo:** SaurabhKaperwan/CSX
- **Último SHA sincronizado:** 0a63c497 (minor fix)
- **Fecha:** 2026-07-19
- **Versión local:** v478
- **Repo local:** Elvizk/Kdrama
- **Aplicado:** Vivibebe extractor, CF bypass per-domain (ConcurrentHashMap), cfGet→app.get en Bollyflix/Uhdmovies/Moviesmod, Lordflix conservado (URL snowhouse activa)

## Workflow de Sync (pasos)

1. Ver commits nuevos desde último SHA:
   ```
   gh api "repos/SaurabhKaperwan/CSX/commits?per_page=15" --jq '.[].commit.message'
   ```
2. Leer diff de cada commit nuevo (excluir los ya evaluados en tabla abajo)
3. Consultar tabla de providers → saber qué ya fue evaluado
4. Aplicar decisiones del usuario (sección de abajo)
5. Build local, commit, push
6. **Actualizar este archivo** con nuevo SHA y versión

## Provider Registry (evaluado)

| Provider | Upstream commit | Local state | Razón |
|---|---|---|---|
| Cloudflare bypass | 7e0708c | ✅ Ya presente | Implementado localmente antes del sync |
| VaPlayer | d7339db | ✅ Incluido | Ya existía localmente |
| HdGharTv | b9cf1ee | ✅ Incluido | Ya existía localmente |
| Anikoto | b9cf1ee | ❌ Excluido | Provider anime puro |
| Castle | f9465f1 | ❌ Excluido | Contenido indio solamente |
| CtgMovies | 4d26499 | ✅ Incluido | Tiene dramas coreanos/chinos |
| Movieblast | e9a50a3 | ❌ Excluido | Mayormente contenido indio (Bollywood/Telugu) |
| Vidup | 8707428 | ✅ Incluido | Contenido general, multi-servidor |

## Provider Exclusions (permanente)

- **Castle:** Solo contenido indio — usuario confirmó excluir
- **Movieblast:** Bollywood/Telugu — usuario confirmó excluir
- **Anikoto:** Provider anime puro — usuario decidió excluir providers anime puros
- **Otros anime puros:** (ver v477 en Historial) 16 providers eliminados en v477

## User Decisions Log

- **v477:** "include all 1080p EXCEPT Indian/anime" — focus on Korean/Chinese dramas
- **v477:** Cloudflare bypass → ya presente, sin acción necesaria
- **v477:** withTimeout fix → diferido a v478
- **v477:** Castle omitido (solo indio)
- **v477:** Movieblast omitido (mayormente indio)

## Pending from Upstream

(Ninguno — sincronizado completamente hasta 8707428)
