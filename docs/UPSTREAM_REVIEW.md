# Upstream review

Modified for My Trakt Wallpapers in October 2026.

## Inspected source

- Repository: <https://github.com/johnneerdael/traktlistbackdrops>
- Revision: `4ade48e9f45de2bb2d2db1a5c35b087593ab2212`
- License: Apache-2.0, retained in `LICENSE`.
- Android modules: `:api` and `:sample`.

The review was done before the feature changes. Existing local generation already removed the need for a TVBG Suite server. A new server was not needed.

## What is reused

| Existing part | Use in this version |
| --- | --- |
| `api/` | Projectivy AIDL service, wallpaper models, events, and update broadcast contract |
| `SettingsActivity` and Leanback UI | TV remote settings screens |
| `TraktOAuthActivity` and QR generator | Device login screen and QR display |
| Trakt models and mapping | List item IDs and movie/TV candidates |
| Retrofit clients | Trakt list data and TMDB artwork requests |
| `WallpaperRenderSpec` | 3840 x 2160 canvas, artwork crop, logo box, and fades |
| `WallpaperRenderer` | Local Android Canvas rendering, extended with metadata and title fallback |
| FileProvider and URI grants | Projectivy access to local JPEG files |
| Existing unit tests | Base tests extended for the changed behavior |

The Kotlin source namespace and `:sample` module name stay in place. The installed app ID, provider authorities, app label, and provider UUID change so both APKs can be installed.

## Confirmed issues and changes

| Before | Change | Effect |
| --- | --- | --- |
| Anticipated and trending catalogs enabled by default | Start with no selected sources | No unselected general titles |
| UI only fetched popular public lists | Add authenticated personal list discovery | Your private and mixed lists can be selected |
| List item calls requested only 20 items and had no page parameter | Read pages using the API response headers | Titles after the first page are included |
| TMDB movie routing checked only `ANTICIPATED_MOVIE` | Use the candidate's movie/TV type | List and trending movies use the movie API |
| Generated images stored in evictable `cacheDir` | Store a bounded collection in private app files | Android cache cleanup does not remove the saved images |
| Filenames used title and year | Identify files by movie/TV type and TMDB ID | Same-name titles do not overwrite each other |
| Plain preferences held OAuth data and the export included all preferences | Encrypt local credentials and restrict preference export | Projectivy backups do not receive login tokens or API keys |
| Network calls and rendering ran inside a Projectivy Binder request | Return saved files and queue WorkManager work | Projectivy does not wait for online rendering |
| OAuth polling used a fixed 5 seconds with no local expiry | Follow the server interval, expiry, and backoff | Expired or rate-limited codes do not cause a polling loop |
| Settings alias assumed the old installed package | Use the new app ID in the alias and service metadata | Projectivy can open this plugin settings beside the original |
| Long-term offline use relied on a last URI | Read only valid local files allowed by the current source snapshot | Offline rotation works without adding another source |

The old crop test expected a rectangle without the configured horizontal bias. Its expected value was corrected. The original crop specification was kept. The OAuth layout was changed to fit a normal 960 x 540 logical TV screen. Settings dialogs use the existing Leanback theme.

## Current API checks

The current [Trakt OpenAPI specification](https://developer.trakt.tv/openapi.json) was checked. Device token exchange uses `code` and `client_id`. It does not send a client secret from the TV. Token refresh uses `client_id`, `redirect_uri`, `refresh_token`, and `grant_type`.

OAuth requests use `https://auth.trakt.tv`; list requests use `https://api.trakt.tv`. The public redirect URI entered in the app must match the registered Trakt app. Device login does not require a hosted callback.

The [authentication guide](https://developer.trakt.tv/docs/authentication-oauth) states that access tokens last seven days and refresh tokens are single-use. Stored token pairs must be replaced together. A failed legacy refresh can require a new login.

The [pagination guide](https://developer.trakt.tv/docs/pagination) requires clients to use the returned page count and effective limit. A page limit of 250 is common. A requested page size must not be treated as proof that the list is complete.

The [rate limit guide](https://developer.trakt.tv/docs/rate-limiting) requires `429` responses to respect `Retry-After`, or use limited backoff if the header is absent. Current standard GET limits are 500 calls per five minutes. They can change, so the plugin must not depend on that number.

The [Trakt API use policy](https://developer.trakt.tv/docs/api-use-policy) permits on-device caches for user features. The [TMDB FAQ](https://developer.themoviedb.org/docs/faq) gives artwork attribution requirements.

The unmodified TMDB blue short logo is saved as `docs/assets/tmdb_logo.svg`. It comes from [the logo served by TMDB's official developer documentation](https://files.readme.io/29c6fee-blue_short.svg), referenced as `logoUrl` by the official FAQ page. Its original view box is `0 0 273.42 35.52`. The About screen uses an Android vector with the same geometry, colors, and aspect ratio. Compact SVG arc flags were expanded to Android-compatible path syntax.

## Verification limits

Local build and unit-test results must be reported from the actual build. A workflow file does not prove a successful GitHub Actions run. Device login, Android scheduling, TV rendering, and the installed Projectivy version need the UGOOS checks in [SETUP.md](SETUP.md#device-checks).
