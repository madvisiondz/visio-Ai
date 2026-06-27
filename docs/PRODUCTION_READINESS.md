# Visio Ai — Production Readiness

> Master evaluation tracker. Phase A = production blockers.

## Phase A — Production blockers ✅ (v2.23.0)

| Item | Status |
|------|--------|
| `fallbackToDestructiveMigration(false)` | ✅ |
| Room `exportSchema = true` + schema JSON committed | ✅ `app/schemas/.../18.json` |
| Migrations extracted to `OasisDatabaseMigrations.kt` | ✅ |
| Migration instrumented tests (16→17, 17→18) | ✅ |
| Release R8 minify + shrink resources | ✅ |
| ProGuard rules (Room, ML Kit, ONNX, NanoHTTPD) | ✅ |
| Optional release signing via `local.properties` | ✅ |
| Debug `applicationIdSuffix = .debug` | ✅ side-by-side install |
| Timber + `OasisLog` domain tags | ✅ |
| Unit tests: `PngShareHelper`, `ImportCatalogMaps` | ✅ |
| Removed hardcoded Supabase URL from build defaults | ✅ |

## Phase B — Scale & performance ✅ (v2.23.1)

| Item | Status |
|------|--------|
| Article rayon browse — Room `PagingSource` (40/page, images first) | ✅ |
| Search debounce 600ms (Home + Catalog) | ✅ |
| `ParayLearnStore` in-memory cache + invalidation on `put()` | ✅ |
| `ParayCameraMatcher` uses `recordsByArticleId()` (no disk read per scan) | ✅ |
| `PhotoroomStorage` single-flight index build + 24h import cache TTL | ✅ |
| `DesignViewModel` render job cancel (no overlapping A4 renders) | ✅ |

## Phase C — Polish ✅ (v2.24.0)

| Item | Status |
|------|--------|
| Hilt DI — `@HiltAndroidApp`, `OasisDatabaseModule` (DB, repository, registries) | ✅ core bindings; ViewModels still use factory |
| Atomic JSON writes — `AtomicJsonWriter` + `writeTextAtomic` on PARAY/registries | ✅ |
| Network security config — manifest `networkSecurityConfig`; Supabase HTTPS-only | ✅ mall LAN cleartext for phone sync IPs |
| Optional backup encryption — AES-256-GCM + Settings toggle/password | ✅ |
| Local crash reports — `LocalCrashReporter` + `OasisLog.ReleaseTree` hook | ✅ Firebase Crashlytics deferred (no `google-services.json`) |

## Optional next (post-MVP)

- Migrate ViewModels to `@HiltViewModel` (remove manual factory wiring)
- Firebase Crashlytics when Firebase project configured
- HTTPS on mall phone-sync server (then tighten base cleartext config)
