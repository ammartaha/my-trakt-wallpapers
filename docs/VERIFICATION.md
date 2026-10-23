# Build verification

Checked on 23 October 2026. Times and dates for this project use Asia/Baghdad.

## Result

- Debug APK and minified release APK: built successfully.
- Unit tests for 1.0.2: all 62 passed on GitHub Linux, zero failed or skipped. On Windows: 61 passed, zero failed, 1 platform-specific skip.
- Android lint for 1.0.2: zero errors; 66 warnings on Windows and 68 on GitHub. The warnings cover unused resources, English UI text, compatibility hints, and dependency versions. They were not hidden with a baseline.
- APK signatures: both verify. The release APK has valid v1 and v2 signatures.
- Release signing certificate: saved RSA 3072-bit release key. SHA-256: `19025a51212f2bd0d86de0162a4ecec7c17dc22c31e7190aa845b48fb0bb13a7`. The debug APK uses the separate Android Debug key. The release key is also configured in encrypted GitHub Actions secrets.
- Built APK label: My Trakt Wallpapers.
- Built APK ID: `com.sww.projectivy.traktwallpapers`.
- Version: 1.0.2 (versionCode 3). Minimum API 23; target API 36.
- The published APK was downloaded, checked against its release checksum and saved signing certificate, and installed over the local build on the UGOOS. Its SHA-256 is `3d602b187c6f1a129758a48c4384e3d5294a50c080c0606fa721755f6b4ae982`.
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
- Settings reloads rebind Leanback menu rows. A regression reproduces the old stale-action failure, then verifies remote OK opens newly loaded list choices after reload. A second test verifies one OK press selects the watchlist, keeps it selected after reload, and a second press removes it.
- Private Development app redirect defaults, exact saved HTTPS values, safe loopback validation, and the required Trakt credit.
- QR login controls fit a 960 x 540 logical TV screen.
- A native graphics test wrote a valid 3840 x 2160 JPEG with descriptions, rating, and a text title fallback. The rendered preview was also viewed locally.

Unit tests use fixture data and a local mock API. Credential tests use an in-memory SecretStore. Real device login and persistence across an APK update have also been checked; a controlled reboot check remains.

Separate live account checks were completed after registration: the private Development app is approved; the Trakt device-code endpoint accepted its client ID and returned a code; and TMDB movie and TV artwork endpoints both returned HTTP 200 with backdrops and logos. No personal API values were placed in source, build settings, test fixtures, or GitHub. A copy for device setup is encrypted with Windows DPAPI in a private folder outside the repository.

## One platform test is skipped on Windows

`androidFileProviderSharesOnlyGeneratedFiles` passed on Linux GitHub Actions. It is skipped on Windows because AndroidX FileProvider's Android path check uses `/`, while the Windows JVM uses `\` paths. See [the AndroidX source](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java). The app still uses the standard FileProvider. The durable cache tests run on both systems.

## UGOOS device checks

Version 1.0.2 was installed on an AM9 PRO running Android 14 / API 34. The original plugin 1.3.15 remains installed beside it. Projectivy Launcher 4.72 is installed.

Confirmed on the device:

- Real Trakt device login and API settings remain available after the signed APK update and a plugin process restart.
- The list and optional catalog menus open after settings reloads. Selected sources remain selected after a plugin process restart.
- Live Trakt checks load the selected movie catalog. Three wallpapers were generated and saved on the UGOOS.
- Projectivy discovers the separate provider and reads its local FileProvider images with URI grants.
- A generated movie wallpaper is visible on the real Projectivy home screen, with artwork, a movie logo, description, rating, year, runtime, genres, and tagline.
- Saved wallpapers remain available after the plugin process is stopped and reopened. This is not a full TV reboot or offline test.
- Android's JobScheduler has queued the plugin's next list check with timing and connectivity constraints. A full remote list-change cycle still needs the test below.

The checks below still need a completed device test:

1. Choose a private list with a movie and TV series. Check that only those titles appear when other sources are off. Fixture tests cover this behavior; a real populated private list was not available for the device check.
2. Add and remove titles in Trakt, then wait for an automatic check while the TV is awake and online. Check that new titles appear and removed titles stop being offered.
3. Disconnect internet and reboot. Check saved wallpaper rotation with the same selection.
4. Reconnect and confirm the saved login and keys still work. Check sleep/wake behavior.

The complete device checklist is in [SETUP.md](SETUP.md#device-checks).

## GitHub Actions status

The fork is [ammartaha/my-trakt-wallpapers](https://github.com/ammartaha/my-trakt-wallpapers). [The first live build](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39742918246) passed all 53 tests, lint, and both APK builds. The downloaded GitHub release APK also passed signature verification and uses the same saved release certificate as the local release.

Release signing secrets are configured. Pushes run checks and builds; version tags also publish the tested signed APK. The workflow can also publish when run manually on a version tag. Pull requests cannot receive signing secrets. See [Actions](https://github.com/ammartaha/my-trakt-wallpapers/actions).

[The release run](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39744425386) passed and published [version 1.0.0](https://github.com/ammartaha/my-trakt-wallpapers/releases/tag/v1.0.0). The APK can be downloaded directly, without extracting an Actions artifact. Its SHA-256 is `42d461b6388cea101e4293b2240859ad2a801d9cece19c9a1852a61beccbcbb4`.

Those two earlier runs were started through `workflow_dispatch`. The owner-signed-in Actions page showed a separate one-time fork block, even though the repository API reported Actions enabled and the workflow active. Its **I understand my workflows, go ahead and enable them** button was enabled on 22 October. Both the [source push](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39765066985) and the [1.0.1 tag push](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39765139278) then started automatically with event `push`.

[The automatic 1.0.1 release run](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39765139278) passed all 60 tests, lint, both APK builds, and publication. [Version 1.0.1](https://github.com/ammartaha/my-trakt-wallpapers/releases/tag/v1.0.1) is published. The release APK SHA-256 is `3bacb28ba14f475600b0910c8973f552c9275d654e344f08b8b9cbd43fd4ef9e`.

Both the [1.0.2 source push](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39925045032) and [1.0.2 tag push](https://github.com/ammartaha/my-trakt-wallpapers/actions/runs/39925049887) started automatically and passed. The release run passed all 62 tests, lint, debug/release builds, signing, and publication. [Version 1.0.2](https://github.com/ammartaha/my-trakt-wallpapers/releases/tag/v1.0.2) fixes settings menus that stopped opening after a reload. The APK is signed with the same release certificate, so updates keep saved settings and wallpapers.

## Reports and preview

- Unit report: `sample/build/reports/tests/testDebugUnitTest/index.html`.
- Lint report: `sample/build/reports/lint-results-debug.html`.
- Preview: `sample/build/reports/renderer-preview.jpg`. This uses synthetic test artwork, not a title from your account.
- Delivery checksums: `dist/SHA256SUMS.txt`.
