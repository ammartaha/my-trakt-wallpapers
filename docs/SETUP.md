# Install and use My Trakt Wallpapers

Modified for this custom plugin in October 2026.

## Start here

The repository, APK build, and release signing are set up for you. You do not need to fork, compile, or make signing keys.

1. [Download the ready APK](https://github.com/ammartaha/my-trakt-wallpapers/releases/latest/download/My-Trakt-Wallpapers.apk) and install **My Trakt Wallpapers** on the TV.
2. Connect your Trakt and TMDB API accounts once, then choose your lists.
3. In Projectivy, select **My Trakt Wallpapers** as the wallpaper provider. Use **Refresh now** for the first images.

The account login and the TV display need your own account and device. The sections below explain these steps. GitHub and local build details are reference information for future maintenance.

## What you need

- UGOOS AM9 Pro with Android 6.0 / API 23 or later.
- Projectivy Launcher with wallpaper provider support.
- A Trakt account and the personal lists you want to show.
- Your own Trakt API client ID and a TMDB v3 API key.
- Internet access for login, list checks, and new artwork.

The TV does all image work. A phone is useful for the Trakt QR login. A computer is only needed to build or transfer an APK if you choose that method.

## 1. Get the APK

Use the [ready release APK](https://github.com/ammartaha/my-trakt-wallpapers/releases/latest/download/My-Trakt-Wallpapers.apk), or the release APK delivered with this project. You do not need a GitHub account to download a public release.

- Debug: `sample/build/outputs/apk/debug/sample-debug.apk`.
- Release: `sample/build/outputs/apk/release/sample-release.apk`.

Both APKs install as **My Trakt Wallpapers**. Use the release APK for normal use. The delivered release and GitHub releases use the same saved signing key. Keep the debug APK for development only. See [APK updates](#apk-updates).

## 2. Install on the UGOOS

1. Copy the APK to a USB drive, or transfer it to the TV.
2. Open the APK with the TV's file manager.
3. If Android asks, allow that file manager to **Install unknown apps**. The exact menu depends on the UGOOS firmware.
4. Select **Install**.
5. Open **My Trakt Wallpapers** from the TV app list.

The custom app ID is `com.sww.projectivy.traktwallpapers`. The original is `com.traktlistbackdrops.tv`. You can keep both installed.

If you already use Android Debug Bridge and have authorized this TV, you can install from a computer:

```powershell
adb install -r .\sample\build\outputs\apk\debug\sample-debug.apk
```

Use the release APK path instead when updating a release installation. USB/file-manager installation does not require ADB.

## 3. Get API access

### Trakt

1. On your phone or computer, sign in to [Trakt API apps](https://developer.trakt.tv/apps).
2. Connect your verified GitHub account if Trakt asks, then create a **Development** app for personal use. Only you and members you add can sign in.
3. Use `http://localhost/` as the **Redirect URI**. Development apps require a loopback address. Leave allowed origins empty for this Android TV device-code flow.
4. Save the app and copy its **Client ID**.
5. In the plugin's API settings, enter the Client ID and the same Redirect URI, including its final `/`.

The device flow displays a code. It does not need you to host a website or callback. The redirect value is used for token refresh. Do not use the old `urn:ietf:wg:oauth:2.0:oob` value. The current [Trakt authentication guide](https://developer.trakt.tv/docs/authentication-oauth) and [OpenAPI specification](https://developer.trakt.tv/openapi.json) describe the device flow. This app does not need or send a Trakt **Client Secret**.

The localhost URI is sent as text inside the HTTPS refresh request. The plugin does not start a local server or send HTTP traffic. Existing Production app settings can keep their registered HTTPS URI. Version 1.0.1 accepts both cases and keeps the exact saved URI.

The [Trakt app requirements](https://developer.trakt.tv/docs/create-an-app) require agreement to its API terms and subscription to the GitHub API notices. These account steps have been completed for your app. Do not create a second app if yours is already listed.

If Trakt marks an API app as unapproved or inactive, fix that app registration in Trakt. Creating more apps does not bypass Trakt limits.

### TMDB

1. Sign in to [TMDB](https://www.themoviedb.org).
2. Open your account **Settings** > **API**.
3. Apply for a developer API key for your personal app.
4. Copy the **API Key (v3 auth)**. Use the v3 key, not the longer API Read Access Token.
5. Enter it in the plugin's API settings and save.

TMDB explains key setup and non-commercial use in its [official FAQ](https://developer.themoviedb.org/docs/faq).

Keep your API settings on the TV. Do not put API keys, Trakt tokens, or a Client Secret in the GitHub source, workflow, issue text, or screenshots.

## 4. Connect Trakt

1. Open **My Trakt Wallpapers**.
2. Open the API settings and save your Client ID, matching Redirect URI, and TMDB v3 key.
3. Select **Connect Trakt**.
4. Scan the QR code with your phone. You can also open the displayed Trakt address and enter the code.
5. Sign in to your own account and approve the app.
6. Leave the TV login screen open until it says connected.

If a code expires, close the login screen and start again. If you get `invalid_grant` or `session not found` during refresh, connect Trakt again. These errors mean the saved login can no longer be renewed. Saved local wallpapers can still be used with the existing selection.

## 5. Choose your lists

1. Select **Load my Trakt lists**.
2. Open **My lists**.
3. Select only the lists you want.
4. Leave **Extra catalogs (optional)** off if you want only your own list titles.
5. Select **Refresh now** and keep the TV online for the first wallpapers.

Private lists work through your approved Trakt account. A list can contain both movies and TV series. Unsupported list item types, such as people, do not become wallpapers. Titles without a usable TMDB ID or backdrop cannot be rendered.

Trending, anticipated, and popular public lists are optional sources. They start off. Use **Load popular lists (optional)** only if you want to choose public lists. The plugin never enables another source as a fallback. An empty selection returns no wallpapers.

List membership is read in full. Duplicate movie or TV IDs appear only once. Only direct movie and show items are used; seasons, episodes, and people are ignored.

Each work run tries at most six titles and renders at most three images. A title with missing metadata or a missing backdrop is skipped for 24 hours. A missing logo uses the title as text. A large list can take several runs to fill the saved collection. Not every list title is stored at the same time.

## 6. Select the plugin in Projectivy

1. Open Projectivy Launcher **Settings**.
2. Open **Appearance** > **Wallpaper**.
3. Choose the wallpaper provider **My Trakt Wallpapers**. Menu names can differ by Projectivy version.
4. Set Projectivy's wallpaper change interval as you want.
5. If needed, open this provider's settings to refresh or change sources.

Projectivy's wallpaper interval controls image rotation. The plugin's refresh interval controls Trakt list checks. They are separate settings.

## Daily use and offline use

The normal list check is once per hour. Use **Check interval** to choose **15 minutes**, **1 hour**, or **6 hours**. Android runs the job when the TV has a network connection. Sleep, power saving, or UGOOS background restrictions can delay it. **Refresh now** requests an extra check.

Automatic image generation waits at least 15 minutes between runs. Manual **Refresh now** can start it sooner. API rate limits still apply. **Refresh on idle exit** controls the refresh request when you leave Projectivy's idle mode.

Saved wallpapers stay in the app's private files. They survive a normal app restart, reboot, and loss of internet. **Saved wallpaper limit** lets you choose **10**, **30**, or **60** images. The default is 60. The collection also has a 512 MiB storage limit. Android **Clear data** and app uninstall remove it. **Clear cache** should not remove this saved collection.

After a complete successful check, added titles can receive wallpapers and removed titles stop being offered. Failed or incomplete checks keep the last complete selection snapshot. An offline TV cannot know that a title was removed on Trakt until it connects again.

If you change the selected sources while offline, the plugin does not offer files from the old selection. Connect once to check the new selection. Projectivy may keep its current image until it asks the provider for another one.

## Data and credentials

| Data | Where it is kept |
| --- | --- |
| Client ID, TMDB key, and Trakt login tokens | Encrypted on the TV using Android Keystore |
| Public redirect URI, selected source IDs, and display settings | Private app preferences |
| Complete list snapshot and generated JPEG files | Private app files |
| GitHub Actions build artifacts | Your repository's Actions storage; no personal API settings are needed |

Projectivy preference export excludes API keys and login tokens. Android app backup is disabled. The plugin has no custom server, analytics service, or public wallpaper upload step. Trakt receives login and list API requests. TMDB receives artwork and metadata requests.

Select **Disconnect and clear saved data** to remove the plugin's saved account data and wallpaper collection. To remove all local app data, use Android **Settings** > **Apps** > **My Trakt Wallpapers** > **Storage** > **Clear data**. To revoke the app's access at Trakt as well, remove it from your Trakt connected apps. Clearing Android data alone does not revoke Trakt's server-side grant.

**About** shows the project credits and TMDB attribution.

## GitHub Actions

This setup is already done in [ammartaha/my-trakt-wallpapers](https://github.com/ammartaha/my-trakt-wallpapers). No action is needed for installation.

- Source changes start unit tests, Android lint, and debug/release builds automatically.
- [Actions](https://github.com/ammartaha/my-trakt-wallpapers/actions) keeps APK and check reports for 30 days.
- A version tag such as `v1.0.0` also publishes the tested release APK under [Releases](https://github.com/ammartaha/my-trakt-wallpapers/releases).
- The release APK uses a saved RSA signing key. The key and passwords are in encrypted GitHub Actions secrets. They are not in source code.
- Pull requests cannot use signing secrets. The build job has read-only repository access. Only the version-tag release job can publish a release.
- Publishing a version tag fails if the saved signing key is missing.

The workflow uses these secrets, which are already configured:

| Secret | Purpose |
| --- | --- |
| `MYTRAKT_RELEASE_KEYSTORE_BASE64` | Saved signing key |
| `MYTRAKT_RELEASE_STORE_PASSWORD` | Key file password |
| `MYTRAKT_RELEASE_KEY_ALIAS` | Key alias |
| `MYTRAKT_RELEASE_KEY_PASSWORD` | Key password |

A private copy of the key is saved outside the source on the build computer. Its password is protected by Windows DPAPI for the current Windows account. Keep the same release key for future updates. Do not replace it with a runner's debug key.

For a future source release, increase `versionCode` and `versionName`, push the change, then push its matching version tag. GitHub builds and publishes the APK. No Trakt or TMDB credentials are needed for this build.

## APK updates

Use the same app ID and signing key for every update. Increase `versionCode` in `sample/build.gradle.kts` for a new release. Install over the existing app using Android or `adb install -r`.

If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the old and new signing keys differ. Build again with the original signing key. Uninstalling also permits a fresh installation, but it removes the saved login, selected lists, and wallpapers.

## Build on Windows

1. Install JDK 17 and Android SDK command line tools or Android Studio.
2. Install SDK Platform `android-36` and Build Tools `35.0.0` with SDK Manager.
3. Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your SDK folder.
4. If Gradle cannot find the SDK, add its path to the ignored `local.properties` file. For example: `sdk.dir=C\:/Users/YOUR-NAME/AppData/Local/Android/Sdk`. Keep the backslash before the drive colon.
5. Open PowerShell in the source folder and run:

```powershell
.\gradlew.bat :sample:testDebugUnitTest :sample:lintDebug :sample:assembleDebug :sample:assembleRelease
```

No API keys are needed for this command. Enter them on the TV after installation.

For a locally signed release, set `MYTRAKT_RELEASE_STORE_FILE`, `MYTRAKT_RELEASE_STORE_PASSWORD`, `MYTRAKT_RELEASE_KEY_ALIAS`, and `MYTRAKT_RELEASE_KEY_PASSWORD` in your build process environment. Keep passwords out of saved shell scripts and source files. With no release key configured, the release build uses the local debug key.

## Device checks

These checks need your UGOOS and your own API accounts. A local unit test cannot prove them.

| Check | Expected result |
| --- | --- |
| Install beside the original | Both apps are listed; Projectivy shows **My Trakt Wallpapers** separately |
| TV remote controls | You can enter API settings, use all list controls, and return to Projectivy |
| Trakt login | QR/device approval completes and the connected status appears |
| Private mixed list | A selected private list supplies both a movie and a TV series |
| More than 250 list items | An item on a later API page can become eligible; no first-page-only result |
| Strict selection | Choose one small known list; no title outside that list is offered |
| Optional catalogs | Trending or anticipated titles appear only after you enable that source |
| Add and remove titles | After **Refresh now** completes, new titles can render and removed titles stop rotating |
| Remove a selected source | Its images are not offered from the old source selection |
| Offline and reboot | With a filled collection and unchanged selection, turn off internet and reboot; saved images still rotate |
| Change sources offline | Old-selection images are not offered; reconnect and refresh to fill the new selection |
| Sleep and wake | List checks resume after the TV wakes and has internet; an idle job can run late |
| 4K and layout | Images are 3840 x 2160; artwork, logo, fades, and text fit your Projectivy rows |
| No logo | The title is shown as text when TMDB has no usable logo |
| Credential export | A Projectivy settings export has no API key, access token, or refresh token |
| Stable update | Install a second build signed with the same release key; login and saved images remain |

For a later-page test, use a small temporary test list first to verify the chosen title has a TMDB backdrop. A large list and bounded rendering mean an eligible title may not be rendered in the first batch.

## Problems and next steps

| Problem | Meaning and next step |
| --- | --- |
| No wallpapers yet | Check API settings, Trakt login, selected sources, and the last refresh status. Run **Refresh now** online |
| Empty list | Check the same list in your Trakt account. No other catalog is used as a fallback |
| HTTP `401` | Login is missing or no longer valid. Connect Trakt again |
| HTTP `403` | Trakt rejected the app or access. Check the client ID, registration approval, and account permissions |
| HTTP `404` | The selected list is missing or no longer accessible. Fetch your lists again and check the selection |
| HTTP `429` | The API asked the app to wait. Let backoff finish; repeated manual refreshes do not help |
| TMDB error | Check that you entered the v3 API key and that the TV can reach TMDB |
| Same image stays in Projectivy | Check Projectivy's image interval and whether this provider has saved eligible files |
| No automatic check after sleep | Wake the TV, check internet access, then use **Refresh now**. Check UGOOS background restrictions if this repeats |
| Source change while offline | Reconnect once. A new selection must be checked before old files can be used for it |
| APK update rejected | Use the same signing key as the installed build |

## Official references

- [Trakt authentication](https://developer.trakt.tv/docs/authentication-oauth)
- [Trakt current OpenAPI specification](https://developer.trakt.tv/openapi.json)
- [Trakt pagination](https://developer.trakt.tv/docs/pagination)
- [Trakt rate limits](https://developer.trakt.tv/docs/rate-limiting)
- [Trakt API use policy](https://developer.trakt.tv/docs/api-use-policy)
- [TMDB API and attribution FAQ](https://developer.themoviedb.org/docs/faq)
- [GitHub workflow triggers](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)
- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
