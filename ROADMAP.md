# Roadmap / Ideas

## Usefulness (highest impact)

- **Localization (i18n)** — currently English-only (`res/values/strings.xml`, no `values-<lang>/` variants at all). Straightforward in scope (translate `strings.xml`), no architecture changes.
- **Multiple wake-up alarms** — `AlarmScreen`/`AlarmStateStore` deliberately support only a single daily alarm today (see CLAUDE.md). Moving to a list would mean an `AlarmStateStore` → Room table migration plus a list UI instead of one form.
- **Search Discover by language** — `RadioBrowserApi.SearchBy` already has `NAME`/`TAG`/`COUNTRY`; the directory's `language`/`languagecodes` fields (already in every search response, just never parsed into `RadioBrowserStation`) would make a natural 4th mode, same shape as the existing three.
- **"Near me" station search** — Radio Browser exposes `geo_lat`/`geo_long` per station and supports distance-based sorting server-side. Bigger than the other Discover additions: needs a location permission request and a real privacy tradeoff to weigh (this app currently requests no location access at all), not just a new search mode.

## Usability

- **Mobile data warning** — warn before starting playback on a metered connection, so users don't burn mobile data unintentionally.
- **Station favicon as auto-icon**: Radio Browser's search results include a `favicon` URL (a station's real logo) that we don't fetch at all — could feed the existing custom-icon pipeline (`IconStorage`) as another auto-fill source alongside the emoji generator, instead of only ever showing a generated emoji for Discover-added stations.
- **Codec shown alongside bitrate in Discover results** — `RadioBrowserStation`/`stationSubtitle()` already show bitrate; the directory's `codec` field (AAC+/MP3/OGG/...) is fetched by every search call and discarded. One-line addition to the same subtitle.
- **Popularity indicator in Discover results** — search results are already sorted server-side by `clickcount` (`RadioBrowserApi.search`'s `order=clickcount`), but the `votes` count itself is never shown to the user — a small badge would help pick between several similar-sounding results.
- **Country flag emoji in Discover results** — `RadioBrowserStation` only parses `country` (the display name); the directory also returns `countrycode` (ISO 3166-1 alpha-2), which converts to a flag emoji via a fixed Unicode code-point offset with no network call or asset needed. Would sit next to the country name in the subtitle.
- **Station homepage link** — the directory's `homepage` field (station's own website) is returned by every search but discarded; a "visit website" action on the Discover result card would need no schema change, since it's only relevant at search time.
- **Warn on stations with SSL cert issues** — the directory flags streams whose HTTPS certificate failed its own check via `ssl_error`; skipping such results by default (or showing a warning icon) would catch a class of station that currently just fails silently once added, similar in spirit to the existing `StreamValidator` reachability probe.

## Technical

- **Wear OS complication/tile** — same `MediaSession` foundation the home screen widget uses; likely a similar-shaped follow-up now that the widget exists.
- **"Register click" with Radio Browser** on play for stations added via Discover (`GET /json/url/{uuid}`) — the directory uses this to rank stations by popularity; we already sort search results by `clickcount` server-side but never contribute to it.
- **More reliable HLS detection for Discover-added stations** — `RadioPlaybackService.isHlsUrl()` currently just checks whether the URL contains `"m3u8"`; the directory's own `hls` flag (0/1, already in every search response) would let Discover-added stations skip that heuristic entirely for streams whose URL doesn't happen to contain `m3u8`.
