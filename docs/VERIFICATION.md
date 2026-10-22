# Build verification

Checked on 22 October 2026. Times and dates for this project use Asia/Baghdad.

## Result

- Debug APK and minified release APK: built successfully.
- Unit tests on Windows: 53 total; 52 passed; zero failed; 1 skipped. On GitHub Linux: all 53 passed, zero skipped.
- Android lint: zero errors; 63 warnings on Windows, 65 on GitHub. The two extra GitHub warnings concern newer Android SDK and build tool versions. Other warnings cover unused resources, English UI text, compatibility hints, and dependency versions. They were not hidden with a baseline.
- APK signatures: both verify. The release APK has valid v1 and v2 signatures.
- Release signing certificate: saved RSA 3072-bit release key. SHA-256: `19025a51212f2bd0d86de0162a4ecec7c17dc22c31e7190aa845b48fb0bb13a7`. The debug APK uses the separate Android Debug key. The release key is also configured in encrypted GitHub Actions secrets.
- Built APK label: My Trakt Wallpapers.
- Built APK ID: `com.sww.projectivy.traktwallpapers`.
- Version: 1.0.0 (versionCode 1). Minimum API 23; target API 36.
- Built APK authorities and settings alias use the new app ID. Android backup and cleartext traffic are disabled.
- Generated BuildConfig contains public API URLs, not personal API keys, OAuth tokens, or a Trakt client secret.
- Source archive excludes local.properties, build folders, .git, signing keys, and personal credentials. A source scan found no literal credential assignments.

## Command and environment

```powershell
.\gradlew.bat :sample:testDebugUnitTest :sample:lintDebug :sample:assembleDebug :sample:assembleRelease --continue --console=plain
```

Windows; JDK 17.0.20.1; Gradle 8.13; Android Gradle Plugin 8.13.2; Kotlin 2.3.0; SDK Platform 36.

The reused upstream revision is `4ade48e9f45de2bb2d2db1a5c35b087593ab2212` from johnneerdael/traktlistbackdrops.

## What the tests checked

- Private mixed movie/TV lists and stable numeric list IDs.
- Full list reads beyond 250 titles and server-clamped page sizes.
- Empty selections and empty lists do not load an extra catalog.
- Duplicate items, unsupported item types, missing headers, changed counts, and truncated pages.
- A failed later page preserves the last complete snapshot and existing saved files.
- Movies from every catalog use TMDB movie endpoints; TV uses TV endpoints.
- Offline files persist outside Android's evictable cache. Deselection, account changes, and removed members block old files.
- Same-name movie/TV files do not collide. Cache count and allowed-member cleanup work.
- API keys and login data do not enter Projectivy preference exports or normal preferences.
- Concurrent token refresh requests use one single-use refresh token once. Rotated tokens replace the old pair.
- An old device-code request cannot restore login state after disconnect.
- Rate-limit delays and fallback backoff.
- Normal cache reordering does not look like newly published data and cause a refresh loop.
- TV settings and About dialogs, all sources off on first use, and the new Projectivy settings alias.
- QR login controls fit a 960 x 540 logical TV screen.
- A native graphics test wrote a valid 3840 x 2160 JPEG with descriptions, rating, and a text title fallback. The rendered preview was also viewed locally.

Tests use fixture data and a local mock API. No personal Trakt or TMDB credentials were supplied. Credential tests use an in-memory SecretStore; Android Keystore behavior still needs the device login and reboot check.

## One platform test is skipped on Windows

`androidFileProviderSharesOnlyGeneratedFiles` passed on Linux GitHub Actions. It is skipped on Windows because AndroidX FileProvider's Android path check uses `/`, while the Windows JVM uses `\` paths. See [the AndroidX source](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java). The app still uses the standard FileProvider. The durable cache tests run on both systems.

## Checks which still need your TV

No Android device was attached to ADB. No APK was installed on the UGOOS during this work.

1. Install beside the original plugin and open the settings from Projectivy.
2. Enter your API details and finish a real Trakt device login.
3. Choose a small private list with a movie and TV series. Check that only those titles appear.
4. Add and remove titles. Use Refresh now, then check automatic refresh with the TV awake and online.
5. Check artwork, logo, description, and ratings over your real Projectivy rows.
6. Disconnect internet and reboot. Check saved wallpaper rotation with the same selection.
7. Reconnect, reopen settings, and confirm the saved login and keys still work. Check sleep/wake behavior.

The complete device checklist is in [SETUP.md](SETUP.md#device-checks).

## GitHub Actions status

The fork is [ammartaha/my-trakt-wallpapers](https://github.com/ammartaha/my-trakt-wallpapers). [The first live build](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39742918246) passed all 53 tests, lint, and both APK builds. The downloaded GitHub release APK also passed signature verification and uses the same saved release certificate as the local release.

Release signing secrets are configured. Pushes run checks and builds; version tags also publish the tested signed APK. The workflow can also publish when run manually on a version tag. Pull requests cannot receive signing secrets. See [Actions](https://github.com/ammartaha/my-trakt-wallpapers/actions).

[The release run](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39744425386) passed and published [version 1.0.0](https://github.com/ammartaha/my-trakt-wallpapers/releases/tag/v1.0.0). The APK can be downloaded directly, without extracting an Actions artifact. Its SHA-256 is `42d461b6388cea101e4293b2240859ad2a801d9cece19c9a1852a61beccbcbb4`.

Both recorded runs were started through `workflow_dispatch`. Push and pull-request triggers are configured. An automatic push run has not yet been observed on this new fork.

## Reports and preview

- Unit report: `sample/build/reports/tests/testDebugUnitTest/index.html`.
- Lint report: `sample/build/reports/lint-results-debug.html`.
- Preview: `sample/build/reports/renderer-preview.jpg`. This uses synthetic test artwork, not a title from your account.
- Delivery checksums: `dist/SHA256SUMS.txt`.
