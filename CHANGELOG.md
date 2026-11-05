# Changelog

## [1.0.10] - 2026-11-05

### Added

- **Experimental subtitle sync:** a per-profile preference, enabled by default, controls automatic subtitle alignment in Settings and setup. Slow devices can disable demuxing while keeping automatic best-match subtitle downloads.
- **Subtitle synchronization feedback:** show a bilingual syncing message while references are read and alignment is calculated.
- **Playback and demux tests:** added lifecycle, coordinator, catalog, Home, and real MKV/MP4 instrumentation coverage, including resume reads, sparse tracks, and missing subtitle tracks.

### Improved

- **Subtitle references:** prefer embedded text tracks, select dense dialogue tracks, decode real cue durations, seek near the playback position, and reduce startup sampling to 20 cues.
- **Alignment reliability:** use strong-match gates and two-window confirmation for plausible matches, with frame-rate scaling and high-correlation near-sync handling.
- **Subtitle downloads:** reject ambiguous multi-CD archives, rank matching disc parts correctly, and filter Portuguese releases mislabeled as Spanish.
- **Playback architecture:** immutable playback requests, deduplicated TMDB mapping, and focused Home/Continue Watching regression tests.

### Fixed

- Handle MKV, MP4, AVI, and MPEG-TS reference extraction with bounded seeks, cancellation, typed failures, and shared playback data-source configuration.
- Prevent late subtitle search, download, demux, or alignment work from changing state after source changes, disabling subtitles, or leaving playback.
- Preserve the active subtitle and timing when a replacement candidate fails to synchronize.
- Report missing reference tracks immediately instead of downloading large streams until timeout.

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
