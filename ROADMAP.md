# Roadmap / Ideas

## Usefulness (highest impact)

- ~~**Localization (i18n)**~~ — done: Russian, Spanish and Simplified Chinese added (`res/values-ru/`, `res/values-es/`, `res/values-zh-rCN/`). Adding another language is just a new `values-<lang>/strings.xml` with the same keys — see CLAUDE.md's Localization section for the process to keep them in sync going forward.
- **Multiple wake-up alarms** — `AlarmScreen`/`AlarmStateStore` deliberately support only a single daily alarm today (see CLAUDE.md). Moving to a list would mean an `AlarmStateStore` → Room table migration plus a list UI instead of one form.

## Usability


## Technical

- **Wear OS complication/tile** — same `MediaSession` foundation the home screen widget uses; likely a similar-shaped follow-up now that the widget exists.
