# Changelog

## [1.0.14] - 2026-11-06

### Improved

- **Monochrome look:** white is the only accent across Home, Details, the player, Settings, setup, and profiles. Red remains for new-episode badges and counts; colour is kept only for status, age ratings, ratings, and third-party brands.
- **Consistent shapes and surfaces:** one set of corner radii and neutral panels replaces the previous mix of radii and blue-tinted surfaces.
- **Top bar:** text tabs with a faint focus pill, an underline on the selected tab, and a round profile avatar.
- **Home billboard:** badge, title, details, a two-line synopsis, More info, and page dots in one left-aligned column.
- **Top 10 row:** reworked ranking numbers.
- **Source picker:** format, size, and languages on the main line; seeds and the release name on a dimmer second line. Long language lists are shortened, for example "EN/RU/IT +4".
- **Details:** season chips share one style, the watched count is easier to read, and episodes in progress show the remaining time.

### Fixed

- The player seek bar no longer shows as full before the video duration is known.

## [1.0.11] - 2026-11-05

### Added

- **Franklix rebrand:** new name and artwork throughout the app, Android TV launcher, setup, README, and release packages. Existing installations keep their application ID, profiles, preferences, and stored keys.
- **AllDebrid QR sign-in:** authorize AllDebrid from setup or Settings and save the returned API key automatically. Manual API-key entry remains available.
- **Guided phone setup:** complete first-run configuration in four steps—TMDB, debrid, optional SubDL, and language/audio/subtitles—with required-key validation and a final confirmation.
- **Phone configuration editor:** later, use Settings to edit service keys, source finders, and playback preferences from a phone or computer on the same local network. Sessions expire after 10 minutes and existing secrets stay hidden.

### Improved

- Use AllDebrid as the default debrid service for new installations.
- Updated release artifacts and self-update checks for the Franklix repository and `Franklix.apk` package.

### Fixed

- Restore D-pad navigation from the debrid API-key field back to the service selector.

## [1.0.10] - 2026-11-05

### Highlights

- **Automatic subtitle sync:** align downloaded subtitles with the dialogue track embedded in the video. The per-profile option is enabled by default and can be changed in Settings or during setup.
- **Clearer feedback:** a bilingual message shows when subtitle references are being read and synchronized.
- **Better playback on slower devices:** disabling synchronization skips demuxing but keeps automatic best-match subtitle downloads.

### Improved

- Select better reference tracks and dialogue cues, account for frame-rate differences, and require stronger matches before applying timing adjustments.
- Read fewer cues during startup and seek near the current playback position to reduce synchronization work.
- Support subtitle references in MKV, MP4, AVI, and MPEG-TS videos with bounded reads and consistent network handling.
- Choose matching disc parts more accurately, reject ambiguous multi-disc archives, and filter Portuguese releases mislabeled as Spanish.

### Fixed

- Keep the current subtitle and timing when a replacement cannot be synchronized.
- Prevent late searches, downloads, or synchronization results from affecting a different source or a playback session that has already ended.
- Report missing reference tracks immediately instead of downloading until timeout.
- Expanded regression coverage for subtitle synchronization, playback lifecycle, catalog handling, Home, and real MKV/MP4 media.

## [1.0.9] - 2026-11-05

### Added

- **Episode center:** browse recently released and upcoming episodes for up to 30 days, follow series from Details, dismiss releases individually, and see new-episode counts on posters and in the top navigation.
- **Episode notifications:** opt in per profile to checks every 12 hours for followed series. Watched and dismissed episodes are excluded, including progress synchronized from Trakt.
- **Continue Watching:** resume directly from the home row, with long-press actions for Details, Remove, and Cancel.
- **My List improvements:** sort by newest episodes, recently added, or title; hide completed series; and show new-episode badges.
- **Playback preferences in setup:** a fourth setup step configures interface language, preferred audio, and automatic subtitles. Language changes are deferred until setup finishes.
- **Per-profile subtitles:** prefer embedded Spanish or English subtitles, with automatic external-subtitle search when the selected language is unavailable.
- **Upcoming episode details and watched counters** on series pages.
- **Media-format badges:** inspect quality, HDR/Dolby Vision, video codecs, hardware/software decoding, audio formats, and subtitle formats in sources and playback.
- **Automatic device optimization:** adapt buffering, thumbnails, memory use, and source selection to detected device capabilities.

### Improved

- **TV performance:** lighter carousel transitions and focus animations, static full-screen artwork, bounded catalog concurrency, six-hour episode caches, and device-specific image caches and buffer defaults for the Nokia Streaming Box 8010.
- **Profiles:** expanded controls for avatars, kids mode, PIN protection, Trakt, Spanish-source preferences, and subtitle defaults.
- **Playback information:** clearer source status, Spanish-source filtering, and actual decoder information during playback.
- **Reliability tests:** added focused regression coverage for playback lifecycle, subtitles, notifications, season selection, Trakt concurrency, completion filtering, and setup preferences.

### Fixed

- Prevented stream resolution or player creation from starting playback after the activity stops, including duplicate players after a quick Home/return sequence.
- Preserved automatic and manual embedded-subtitle selection when recreating the player.
- Prevented changing seasons from reusing the previous season's episode or source list.
- Serialized Trakt token refreshes and scrobbles, prevented stale refresh failures from unlinking renewed accounts, and avoided repeatedly refreshing new 24-hour tokens.
- Kept episodes pending when notifications are disabled, posting fails, or the five-notification limit is reached instead of incorrectly marking them as delivered.
- Prevented series with failed season downloads from being incorrectly classified as completed and hidden from My List.
- Updated the setup completion summary to show the newly selected interface language before the deferred restart.
