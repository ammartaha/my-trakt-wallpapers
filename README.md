# My Trakt Wallpapers

Modified from [johnneerdael/traktlistbackdrops](https://github.com/johnneerdael/traktlistbackdrops), commit `4ade48e9f45de2bb2d2db1a5c35b087593ab2212`.

A Projectivy Launcher wallpaper plugin for Android TV, including the UGOOS AM9 Pro. It reads your selected Trakt lists and makes 3840 x 2160 JPEG wallpapers on the TV. It needs no server, website hosting, or computer left on.

**[Download the APK](https://github.com/ammartaha/my-trakt-wallpapers/releases/latest/download/My-Trakt-Wallpapers.apk).** No build tools are needed.

Install **My Trakt Wallpapers**, connect your API accounts once, and select your lists. In Projectivy, choose this wallpaper provider. Leave extra catalogs off to show only your lists. See the [setup guide](docs/SETUP.md) for the exact TV menus.

The [GitHub repository](https://github.com/ammartaha/my-trakt-wallpapers), automatic builds, and saved release signing key are already set up. These are not setup steps for you. The signing key and passwords are kept out of the source and APK.

## Main features

- Your own Trakt lists, including private lists with movies and TV series.
- Only selected sources. Trending and anticipated titles are off by default.
- Original 4K artwork, logo placement, crop, and dark fades.
- Title fallback when no logo is available. Description, rating, year, runtime, and genres when available from TMDB.
- Automatic list checks on the TV and a manual refresh button.
- Saved wallpapers for offline use. Choose a limit of 10, 30, or 60 images, with a 512 MiB storage cap.
- API settings and Trakt login on the TV. No personal API keys or login tokens in the APK or GitHub Actions.

If you change the selected sources while offline, connect once to load the new selection. The plugin does not offer wallpapers from the old selection. After a successful online check, removed list items stop being offered. A TV which is offline cannot detect remote list changes.

## Install beside the original

| Item | Custom plugin |
| --- | --- |
| App name | My Trakt Wallpapers |
| App ID | `com.sww.projectivy.traktwallpapers` |
| Provider UUID | `a9cb3f6c-421d-45b1-bd7f-d23b34c7d921` |
| Android | Android 6.0 / API 23 or later |
| Original app ID | `com.traktlistbackdrops.tv` |

The app IDs, provider authorities, and UUIDs are different. Installing this app keeps the original plugin installed.

## Build

Use JDK 17 and Android SDK Platform 36. On Windows:

```powershell
.\gradlew.bat :sample:testDebugUnitTest :sample:lintDebug :sample:assembleDebug :sample:assembleRelease
```

On Linux or macOS, first run `chmod +x gradlew`, then use `./gradlew` instead. No Trakt or TMDB credentials are needed to build.

| Output | File |
| --- | --- |
| Debug APK | `sample/build/outputs/apk/debug/sample-debug.apk` |
| Release APK | `sample/build/outputs/apk/release/sample-release.apk` |
| Test report | `sample/build/reports/tests/testDebugUnitTest/index.html` |
| Lint report | `sample/build/reports/lint-results-debug.html` |

Published APKs use the saved release key so updates can keep your settings and wallpapers. A separate local build uses a debug key unless you configure the release signing environment. See [GitHub Actions](docs/SETUP.md#github-actions).

## How it works

1. You connect Trakt with a QR code or device code.
2. You fetch your lists and select the sources you want.
3. WorkManager checks those sources while the TV has internet access.
4. The plugin downloads TMDB art and renders wallpapers on the TV.
5. Projectivy reads saved local files through FileProvider.

The source list is read in full using Trakt pagination headers. Each work run tries at most six titles and renders at most three images. Automatic generation waits at least 15 minutes between runs; manual refresh can run sooner. WorkManager survives reboot, but Android can delay work during sleep or power saving.

## Source and tests

The original Projectivy AIDL API and Android Canvas renderer are reused. The source namespace and `:sample` module name are kept to avoid an unnecessary source move. See [the upstream review](docs/UPSTREAM_REVIEW.md) for the exact changes.

GitHub Actions runs unit tests, Android lint, and APK builds on pushes, pull requests, and manual runs. Version tags also publish a tested, signed APK to Releases. Pull requests cannot use the release signing secrets. UGOOS hardware tests are listed in the guide.

Local results and remaining device checks are in [VERIFICATION.md](docs/VERIFICATION.md).

## Credits and privacy

Based on the Projectivy wallpaper provider sample and the work in `johnneerdael/traktlistbackdrops` and `z9m/projectivy-tvbgsuite-plugin`. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Artwork and metadata come from [TMDB](https://www.themoviedb.org). This product uses the TMDB API but is not endorsed or certified by TMDB. Personal list membership comes from [Trakt](https://trakt.tv).

My Trakt Wallpapers uses Trakt but is not endorsed or certified by Trakt.

The plugin stores its data on your TV. It sends requests to Trakt and TMDB for the features you use. It does not upload your settings, tokens, or wallpapers to a custom server. See [data and credentials](docs/SETUP.md#data-and-credentials) for storage and deletion details.
