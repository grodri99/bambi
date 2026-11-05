# Bambi / ShieldStream agent guide

## Project overview

Bambi is an Android TV streaming client written in Kotlin with Jetpack Compose, Hilt, Retrofit, AndroidX Media3, and Coil. It uses TMDB for catalog metadata, Trakt for synchronization, and user-configured debrid/source providers for playback. The app supports multiple local profiles, per-profile watch state and preferences, external subtitles, and episode release tracking.

- Stable application ID: `com.shieldstream.tv`.
- Debug application ID: `com.shieldstream.tv.debug`; it is a separate installation with separate data.
- Minimum SDK: 21. Target SDK: 35. TV landscape UI.
- UI text uses `tr("Español", "English")`. Keep both languages in user-facing text.
- The project is GPL-3.0. Third-party services and components retain their own terms and licenses.

## Modules

- `app`: `MainActivity`, Hilt application/configuration, navigation, app-level DI, update handling, and episode-notification WorkManager code.
- `core:model`: JVM models and pure model logic. No Android UI dependencies.
- `core:domain`: JVM repository contracts, playback/source rules, episode rules, navigation holder, and coroutine helpers.
- `core:network`: Retrofit APIs and TMDB/Torrentio/Real-Debrid DTOs.
- `core:data`: Android repositories, profile-aware persistence, Trakt, subtitles, updates, and Hilt bindings.
- `core:media`: Media3 playback infrastructure and device capability detection.
- `core:ui`: shared Compose components, TV focus/scrolling behavior, icons, and theme.
- `feature:home`: Home, search, My List, episode center, and top navigation.
- `feature:details`: catalog Details, episode/season selection, source resolution, and playback handoff.
- `feature:player`: playback, audio/subtitle tracks, external subtitles, skip segments, and next episode.
- `feature:settings`: setup, settings, profiles, Trakt linking, and update UI.

Keep domain/model logic free of Android UI dependencies. New repository implementations usually need a `@Binds` binding in `core/data/.../di/DataModule.kt`.

## Important behavior and ownership

### Profiles and preferences

Watch progress, My List, followed shows, dismissed/notified episodes, and playback preferences are profile-aware. Interface language is app-wide and takes effect after restart; audio and subtitle preferences belong to the active profile.

The setup has four numbered steps: catalog, debrid, extra subtitles, then language/audio/subtitle preferences. A language change made in step 4 is deliberately deferred until Finish so it does not restart the setup midway. Do not move these preferences back into Welcome: doing so previously pushed Start/Later off-screen. Welcome tiles use equal weights, not fixed widths that overflow a 1080p TV.

### Navigation and playback

`SelectedContentHolder` carries the selected item, optional episode target, playback queue, and one-shot autoplay state. Continue Watching uses Details as an autoplay handoff so source resolution, Spanish-source policy, and resume-position behavior stay centralized. Do not build a second playback-resolution path in Home.

Continue Watching opens directly on the resumable item. OK resumes playback; long-press offers Details, Remove, and Cancel. One-shot holder flags must be consumed/reset to prevent delayed playback on a later Details screen.

### Episode tracking

Use these shared paths rather than duplicating release/watched logic:

- `EpisodeReleaseRepository`: recent unwatched episodes and upcoming releases.
- `EpisodeTrackingRepository`: followed shows, dismissed episodes, notified episodes, and opt-in notification setting.
- `NewEpisodes`: pure candidate/date/release rules and date windows.

Recent releases use a 30-day window; upcoming releases use the next 30 days. Dismissal is per episode, so a newer episode can produce a new badge. Counts currently inspect the latest aired season, not the show's complete history. The episode center loads only after its tab is first opened; the top-bar count appearing after that first visit is intentional and avoids startup request bursts.

Notifications are opt-in, followed-shows-only, and run through periodic WorkManager checks (12 hours with network). The first run establishes a baseline to avoid notifying for old releases. Preserve watched/dismissed filtering and Trakt synchronization. `@HiltWorker` requires the AndroidX Hilt compiler, worker factory, and `Configuration.Provider` wiring in `App.kt`.

### Persistence and credentials

Most persistence uses profile-specific `SharedPreferences`; there is no Room database for watch state. Tokens and user keys belong in `TokenStore` and the gitignored `local.properties`. Never print, commit, or copy credentials into notes, tests, screenshots, or documentation.

## Performance rules learned on the physical TV box

These are measured project-specific rules, not generic Compose advice. The reference device is the Nokia Streaming Box 8010. Lightweight UI was Off during several measurements, so the normal path must also be efficient.

### Full-screen artwork

- Do not add continuous backdrop zoom or a full-screen crossfade. Removing a 12-second zoom and a 350 ms crossfade reduced a measured path from 133 ms to 48 ms.
- The featured carousel uses static artwork and immediate slide changes. Preserve this unless a physical-device A/B test proves an alternative is faster.
- Lightweight mode skips changing row backdrops. Normal mode retains them. Do not request a new backdrop on every focus movement; the existing delayed hero update avoids that.
- Avoid new full-screen shadows, blur, entrance animations, or simultaneous backdrop compositing without measurement.

### Recomposition and focus

- Cache formatted episode/date labels with `remember` at the item level. Caching these labels reduced a measured path from 97 ms to 44 ms. Do not repeatedly construct `SimpleDateFormat` during row recomposition.
- Avoid reading rapidly changing focus state during whole-screen composition. `RowsBrowser` deliberately uses snapshots/listeners for saved focus and delayed hero updates.
- Playback position updates are frequent. Episode-release flows should react to meaningful watched-key/dismissal/candidate changes, not every position update.
- Keep visible poster focus feedback. The current `1.04x` scale is intentional; removing scaling previously gave no speed improvement and made feedback worse.
- Preserve the order of clickable/focus handling before scaling in `PosterCard`; changing it caused vertical list jumps.
- Use explicit focus requesters/properties where geometric focus chooses the wrong TV destination. Test Back, held Up/Down, and returning from Details/player.

### Network and concurrency

- Use `mapConcurrent(concurrency = 4)` for batches of show/season requests. Do not launch 40–50 unrestricted TMDB calls.
- `CatalogRepositoryImpl` caches airing and season data for six hours and uses per-key mutexes to deduplicate in-flight requests. Preserve both behaviors.
- Do not eagerly load all episode statuses at app startup merely to populate a badge.
- Full-season completion checks are expensive and should remain user-triggered and bounded.
- Image loads and full-screen composition are different costs; avoid new image requests unless they solve a measured problem.

### Changes that did not help in testing

Do not repeat these as presumed optimizations without new evidence:

- `w500` instead of `w780` hero images: no frame-time gain and extra image requests.
- Nested prefetch reduced from two cards to one: effectively unchanged. Keep two.
- Removing poster scaling: no improvement and worse visual feedback.
- A longer hero debounce: no improvement in the tested path.

Memory was not the observed bottleneck. Prioritize UI-thread work, draw submission, frame deadlines, and input latency.

## Compose/TV layout pitfalls

- Design for D-pad navigation and 1920x1080 output on the Nokia, not touch or phone layouts.
- Use `verticalScroll` for preference forms that can exceed the available height. Use equal-weight choices when every option must have the same width.
- Checkmark text changes intrinsic button width; use fixed/equal-weight containers for aligned options.
- Some Shield rendering paths lose clipping on scaled layers. Existing code often draws a shaped background directly instead of relying on clip; preserve that pattern where noted in comments.
- `onBackKey` handles Compose TV focus-group behavior; do not replace it casually with a generic back listener.
- Row keys and virtual indexes preserve focus in looping rows and after item removal. Do not replace them with unstable positional keys.

## Build and tests

`gradlew` may not be executable in this checkout; invoke it through `bash`.

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" bash ./gradlew test :app:assembleRelease
```

Unit tests exist mainly in `core:model`, `core:domain`, `core:data`, and `core:media`. Add focused tests there for pure rules, persistence mappings, date filtering, and source/playback policy. There is no comprehensive Compose/UI or end-to-end suite. Compilation and unit tests do not validate D-pad focus, direct playback, notification delivery, or setup layout; verify those on a device.

Before changing code, inspect `git status` and relevant diffs. Multiple agents may edit this tree; do not revert unrelated work. Keep one measurable change per test build when evaluating performance.

## Nokia production install

Use `/Users/javimolina/github/bambi-fork`, not the older checkout. The box is `192.168.0.174:5555` (Android 14, `armeabi-v7a` userspace).

```bash
adb connect 192.168.0.174:5555
adb -s 192.168.0.174:5555 shell getprop ro.product.model
adb -s 192.168.0.174:5555 shell dumpsys package com.shieldstream.tv \
  | rg 'versionCode|versionName'
```

The new APK's `versionCode` must be equal to or newer than the installed one. Do not force a downgrade with `adb install -d`. Verify the release certificate matches the stable app; the expected SHA-256 is `10f85fc97479af56ae45f6bc9545c9a3d6d23676d87f796533887d97fc69f764`.

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" bash ./gradlew test :app:assembleRelease
adb -s 192.168.0.174:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s 192.168.0.174:5555 shell am broadcast \
  -a androidx.profileinstaller.action.INSTALL_PROFILE \
  com.shieldstream.tv/androidx.profileinstaller.ProfileInstallReceiver
adb -s 192.168.0.174:5555 shell cmd package compile \
  -m speed-profile -f com.shieldstream.tv
adb -s 192.168.0.174:5555 shell am start \
  -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER \
  -n com.shieldstream.tv/.MainActivity
```

Check `pidof com.shieldstream.tv` and launch logs for fatal exceptions. Do not clear application data merely to reach setup; use Settings → Setup guide.

## Measuring UI performance

Use the same screen and warmed-up remote-key sequence for A/B comparisons. Billboard navigation and poster-row navigation are different workloads. Cold startup and first-time image decoding create misleading outliers.

```bash
adb -s 192.168.0.174:5555 shell dumpsys gfxinfo com.shieldstream.tv reset
# Perform the fixed navigation sequence.
adb -s 192.168.0.174:5555 shell dumpsys gfxinfo com.shieldstream.tv \
  | rg -A11 -B2 'Total frames rendered|Janky frames|50th percentile|90th percentile|Number Slow'
```

Small samples are noisy. Keep changes only when they produce a repeatable improvement on the physical box. Row navigation still has a known performance gap; prioritize focus/render cost and input latency over release-build tweaks.
