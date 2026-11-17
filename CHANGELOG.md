# Changelog

## [1.0.7] - Bambi

Bambi 1.0.7 incorpora todas las funciones de [Franklix](https://github.com/javiermolinar/franklix) (fork de Bambi
de Javier Molina, versiones 1.0.7 a 1.0.21 de su historial, abajo) con el aspecto de Bambi: barra superior en vez
del menú lateral, rojo de Bambi en vez del estilo monocromo, logo, iconos y nombre de Bambi, letra del sistema y
colores de perfil de siempre. Real-Debrid sigue siendo el debrid por defecto.

### Novedades propias de esta versión

- **Iconos de perfil:** OK sobre el avatar al editar un perfil abre un selector a pantalla completa con 978 iconos de
  103 series, por filas como en Netflix; la primera opción deja la inicial sobre el color. Las imágenes no van en la
  app: se cargan desde los servidores de Netflix (lista de URLs tomada de
  [Bring-Back-Netflix-Icons](https://github.com/Angel2mp3/Bring-Back-Netflix-Icons), licencia MIT). Si alguna deja de
  cargar, se ve la inicial. La primera vez que se abre la app (tras la intro, sin prisa) se guardan todos en la Shield
  y desde entonces salen al instante, también sin conexión.
- **Iconos propios de Bambi:** el selector empieza por un grupo «Bambi» con 7 ilustraciones del gatito (sentado,
  asomándose, sonriendo, agazapado, con palomitas, jugando y dormido). Van dentro de la app: salen al instante y
  sin conexión desde la primera vez.
- **Foto propia en el perfil:** en el selector de iconos, «Subir foto» enseña un QR; con el móvil (misma wifi) se elige
  una foto, se recorta en cuadrado en el propio móvil y aparece en el perfil al momento. Se guarda en la Shield.
- **Cambiar de fuente si la elegida falla** (Ajustes → Reproducción, activado por defecto): desactivado, si una fuente
  elegida a mano no funciona no se prueba otra; se vuelve a la ficha con la lista de fuentes abierta y el motivo.
  «Reproducir» sigue saltando sola.
- **Formato de imagen** (reproductor → pistas → Reproducción): Original (con bandas negras), Ampliar (sin bandas,
  recorta los lados) o Estirar (sin bandas, deforma). Se recuerda para lo siguiente que se vea. Si las bandas vienen
  grabadas dentro de un vídeo 16:9 (remux de Blu-ray), la primera vez se miden en unos fotogramas de la película y
  se amplía en consecuencia.
- **Quitar una serie de Episodios:** mantener OK sobre cualquier serie (disponible o próxima) abre un menú con
  «Descartar episodios nuevos» y «Quitar de Episodios». Quitarla también deja de seguirla; vuelve si se sigue a mano
  desde su ficha. Además, unos segundos vistos (un vídeo de error del addon, una prueba) ya no meten la serie en
  Episodios: hacen falta 30 s o haberla terminado.
- **Episodios:** la pestaña marca las novedades con un punto rojo en vez del número.
- **Multidebrid de verdad en todas las fuentes:** The Pirate Bay, Knaben, Nyaa, el buscador de castellano y Comet ya
  funcionan con el servicio que tengas activo, no solo con Real-Debrid. TorBox tiene resolución propia (como
  Real-Debrid y AllDebrid): la Shield le pide el torrent, elige el capítulo dentro de los packs y pide el enlace; además
  pregunta qué torrents tiene ya en caché para marcarlos con ⚡. Premiumize, Debrid-Link y Offcloud resuelven esos
  torrents a través de Torrentio con tu clave (ahí solo se ofrecen capítulos sueltos, no packs, porque Torrentio no
  sabría qué capítulo elegir). Comet usa el servicio y la clave activos.
- **Nuevo logo:** el gatito negro jugando con la bobina de película y las palomitas, sobre rojo. Cambia el icono
  de la app, el banner de Android TV, el logo de arriba a la izquierda, el selector de perfiles y el asistente.
- **Nueva entrada animada:** sobre el rojo del logo crece una sombra y el gatito cae de espaldas desde arriba, se
  aplasta, rebota y da un latigazo con la cola; saltan las palomitas de debajo, se balancea jugando con la bobina y
  lanza la cinta, que se desenrolla ondeando por la pantalla; luego caen las letras de BAMBI. Mientras espera mueve
  la cola y parpadea. La app ya se abre en rojo (antes eran unos segundos de negro). OK o Atrás la saltan.
- **Nueva transición a la app:** en vez de los haces de luz y el fundido a negro, la pantalla se convierte en un
  fotograma de una tira de película (se aleja y aparecen las perforaciones), la película avanza y el fotograma
  siguiente es «¿Quién está viendo?», que vuelve a ocupar la pantalla. La intro al volver a la app hace lo mismo con la
  pantalla en la que estabas.
- **Intro sin tirones:** lo pesado del arranque (montar la pantalla de debajo, decodificar las piezas, preparar el
  dibujo) se hace antes de que empiece a moverse; la precarga del Inicio decodifica menos imágenes a la vez y sin
  bitmaps "hardware" mientras se ve la intro, y deja lo que no corre prisa para después. Medido en la Shield del
  salón: 60 fps de principio a fin.
- **Temporizador de apagado en el reproductor:** desactivado, 15, 30, 45, 60 o 90 minutos, o «al terminar el
  episodio». Al vencer pausa la reproducción y vuelve a Inicio; con «al terminar el episodio» no encadena el siguiente.
- **Velocidad de reproducción:** 0,75x, 1x, 1,25x, 1,5x y 2x desde el panel de Reproducción del mando. Si el audio
  va en Dolby/DTS directo, se decodifica a PCM (con FFmpeg para DTS y TrueHD) para que la velocidad se aplique de
  verdad, y al volver a 1x recupera el audio directo. Los subtítulos, internos o externos, van a la misma velocidad.
- **Páginas de persona:** al tocar un actor o director se abre su ficha con foto, biografía y filmografía; cada
  título lleva a su ficha normal para elegir fuente y reproducir.
- **Reproduce algo:** botón en el Inicio que elige al azar lo siguiente que toca (A continuación, estrenos,
  Seguir viendo, Mi lista o el catálogo) y lo abre listo para reproducir.
- **Temas por perfil:** cada perfil elige su acento (Bambi, Océano, Bosque, Oro, Violeta o Rosa). El color
  se aplica al progreso, focos, novedades, reproductor y fichas, y se guarda con el perfil.
- **Marcar fuente rota:** mantén OK en una fuente para descartarla. Deja de ofrecerse al momento en todos los
  buscadores que traigan ese mismo torrent, caduca a los 60 días y se puede restaurar desde Ajustes → Fuentes rotas.
- **Elección de fuente, de una vez:** «Reproducir» reproduce la primera de la lista de Fuentes, marcada arriba
  («▶ Se reproduce al pulsar Reproducir»). Ajustes → *Al pulsar Reproducir* (calidad primero / castellano primero /
  solo castellano) y *Calidad preferida* (4K Dolby Vision, 4K HDR, 4K sin HDR, 1080p, 720p). La resolución manda
  sobre el HDR y, dentro del castellano, la calidad sobre lo seguro que esté el detector. Las fuentes sin calidad en
  el nombre la toman del fichero, así que las exclusiones ya no se cuelan. Si eliges una fuente a mano y falla, se
  salta a la más parecida (idioma, resolución, HDR, audio, códec, tamaño) y el aviso dice a cuál.
- **Descargas para ver sin conexión:** en la ficha, Más → Descargar… ofrece este episodio, los siguientes, la
  temporada completa o solo lo no visto, con tope de calidad (la mejor, 1080p o 720p). Usa la mejor fuente que ya
  esté en el debrid; el enlace se pide al empezar cada descarga (no caduca en la cola). Sigue en segundo plano y se
  retoma si se corta. Indicador de progreso arriba a la derecha, que abre Descargas (pausar, reanudar, borrar,
  reproducir). Lo descargado se reproduce desde el disco también desde la ficha. Ajustes → Carpeta de descargas.
- **Panel «Más» rediseñado:** lista lateral por secciones (episodio / serie) que actúa sobre el episodio en el que
  estabas, no solo el último abierto.
- **Editor de subtítulos en vivo:** en el reproductor, Subtítulos → Apariencia pausa el vídeo y enseña un
  subtítulo de ejemplo sobre la imagen mientras cambias tamaño, posición, fuente, color, fondo (también negro
  translúcido), borde (contorno o sombra) y estilo incrustado. Al salir sigue la reproducción.
- **Panel del reproductor:** Ajustes permite elegirlo transparente (como siempre) o en tarjeta.
- **Reparto y equipo opcional:** Ajustes permite ocultar la fila de actores y directores en las fichas.
- **Peticiones familiares:** en un perfil infantil, Buscar enseña bloqueado lo que no es infantil para pedírselo a
  un adulto. Cualquier perfil adulto lo aprueba o descarta desde Ajustes; al aprobar se desbloquea para ese perfil y
  entra en su Mi lista.
- **Audio y subtítulos por serie:** la pista elegida a mano se recuerda por perfil y título (también «sin
  subtítulos») y se vuelve a aplicar en el siguiente episodio.
- **Interfaz de Bambi 1.0.6** con todas las funciones nuevas: portada con destacados y categorías al abrir el Inicio,
  pósteres estrechos con borde blanco, títulos de bloque en rojo, paleta azulada en Ajustes y perfiles, negritas y
  barra de progreso roja del reproductor de siempre. "A continuación" vuelve a mostrarse como póster.
- **Intro de Bambi al abrir:** animación de entrada mientras se preparan por detrás las filas, los destacados y las
  carátulas del Inicio, que aparece ya pintado. Se salta con OK o Atrás. Ajuste *Intro de Bambi siempre*: activado,
  sale cada vez que se vuelve a la app; desactivado (por defecto), solo al arrancar o al volver tras más de 30 minutos,
  cuando hay que recargar el Inicio.
- **Inicio guardado en la Shield:** el último Inicio se enseña al instante y se actualiza en segundo plano.
- **Pantalla de carga del perfil:** si al elegir perfil el Inicio aún no está listo, avatar con anillo rojo.
- **Elementos por fila de Inicio** ahora sí amplía las filas: se piden a TMDB las páginas necesarias (hasta 100
  títulos) y "Recomendado para ti" pide a Trakt hasta ese número (antes 20 y 40 fijos). Al cambiarlo, el Inicio se recarga.
- **Elegir fuente:** el nombre completo de la fuente enfocada se desplaza en vez de cortarse con "…".
- **Episodios:** el póster enfocado ya no se corta por la izquierda.

---

# Historial de Franklix (javiermolinar/franklix)

## [1.0.21] - 2026-11-11

### Added

- **My List item actions:** hold OK on a My List poster, either on Home or the full My List screen, to open Details, remove the item, or cancel.
- **Consistent action menus:** Continue Watching, Up Next, episodes, addons, and exit confirmation now use the same compact bottom action menu.

### Improved

- **Home catalog:** removed the redundant Trending today row from the All tab and corrected the Top 10 labels to describe their weekly TMDB data.
- **TV focus:** made initial focus and focus restoration more reliable when opening menus, switching views, or removing items.

### Fixed

- Prevent the OK release that opens a long-press menu from accidentally triggering its focused action.
- Preserve a useful focus destination after removing an item from My List.

## [1.0.20] - 2026-11-11

### Fixed

- **Missing logo:** the logo assets shipped in 1.0.19 were opaque near-black tiles, so they disappeared against the dark backgrounds of the phone configuration page, the setup screens and the profile picker. They are now transparent, so the mark shows wherever it is drawn.

## [1.0.19] - 2026-11-11

### Added

- **Per-episode watched state:** the Details action dock can now mark the selected episode as watched or unwatched, without playing it. Previously only a whole season could be toggled from the dock.
- **Episode options menu:** holding OK on an episode card opens a modal with Play episode, Mark as watched/unwatched, and Cancel. The hint under the episode row documents the gesture.

### Fixed

- Finishing a stream now always marks it watched, including sources whose container reports no duration.
- The Trakt badge on the profile picker is green instead of Trakt red, so a linked profile no longer reads as a warning.
- The mobile configuration page serves the current logo instead of the old mascot.

### Improved

- Updated the logo in the repository documentation to the current mark.

## [1.0.18] - 2026-11-10

### Added

- **Trakt ratings:** rate movies, overall shows, and selected episodes with a compact star picker.
- **Stremio addon manager:** add, replace, remove, and test multiple addons from Android TV or the mobile configuration page, with manifest-provided names and logos.
- **Compact action menus:** use the new bottom-dock style for Details actions and exit confirmation.

### Improved

- Show manifest URLs while typing in both addon editors.
- Preserve existing addon configuration and keep unchanged manifest values private during mobile updates.

### Fixed

- Restore D-pad focus after service-key validation errors.
- Always include the addon list in mobile configuration responses, including when it is empty.

## [1.0.17] - 2026-11-07

### Added

- **AllDebrid magnet playback:** use The Pirate Bay, Knaben, and Nyaa with AllDebrid. Upload magnets, check cached availability, select the requested movie or episode, and unlock the playback link directly.
- **Direct AllDebrid account validation:** verify API keys and premium status through AllDebrid instead of Torrentio.

### Improved

- Keep independent provider results available when another source provider fails.
- Wrap long service status messages without squeezing or truncating provider details.

## [1.0.16] - 2026-11-07

### Added

- **Android TV integration:** publish resumable movies and episodes to Play Next and a dedicated Franklix Continue Watching channel, with profile-aware resume links and high-resolution landscape artwork.
- **Source exclusions:** filter sources by quality, video features, and advertised audio codecs. Use Show all in the source list for a temporary override.

### Improved

- Use the current Franklix logo on profile, setup, and phone-configuration screens.
- Remove the redundant centered buffering indicator during playback.

## [1.0.15] - 2026-11-07

### Added

- **Navigation rail:** move the main destinations into a dedicated left rail for clearer TV navigation.
- **Backdrop controls:** optional soft backgrounds and high-resolution artwork settings for Home.

### Improved

- **Franklix artwork:** new minimalist launcher icon and Android TV banner.
- **Profile avatars:** muted two-color gradients inspired by forest, sea, and other natural tones.
- **Visual consistency:** refined typography, icons, badges, cards, and surfaces across Home, Details, playback, setup, and Settings.
- **Home artwork:** centralized backdrop selection and added focused regression coverage.

### Fixed

- Preserve the selected source when resuming playback.

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

- **Cambio de nombre a Franklix (no aplica en Bambi):** new name and artwork throughout the app, Android TV launcher, setup, README, and release packages. Existing installations keep their application ID, profiles, preferences, and stored keys.
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
