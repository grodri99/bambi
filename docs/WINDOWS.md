# Bambi para Windows

Port de escritorio de Bambi (Android TV). Mismo aspecto que la tele: barra superior, logo, rojo Bambi (o el acento
del perfil), pósteres con foco y escala, y navegación con flechas como con el mando.

## Diseño

Mismo lenguaje que la tele, llevado a escritorio: negro cálido, superficies con filo fino, letra Segoe UI Variable de
Windows 11, barra superior con pestañas de texto y la raya del color del perfil bajo la activa, pósteres que se
levantan con sombra al pasar por encima, botón principal blanco y secundarios de cristal, Top 10 con números huecos,
etiquetas 4K en dorado y Ajustes con categorías a la izquierda y tarjetas a la derecha, como una app nativa.

## Stack y por qué

| Pieza | Elección | Motivo |
|---|---|---|
| Interfaz | **Compose Multiplatform 1.8 (desktop)** | Es el mismo Compose que la app de la tele: las pantallas se portan casi línea a línea, con los mismos tokens de color, formas y animaciones. |
| Lógica | **`:core:model`, `:core:domain`, `:core:api`, `:core:streams`** (JVM puro) | Los mismos módulos que usa la tele: catálogo TMDB, DTOs, perfiles (`Profile`, `ProfilePin`, `ProfileTheme`) y **toda la búsqueda de fuentes** (`StreamSearch` con Torrentio, Peerflix, TPB, Knaben, Nyaa, Comet y addons). Lo que era Android (Hilt, DataStore, `Uri`, `Log`) quedó detrás de interfaces (`StreamTexts`, `StreamSecrets`, `StreamSettings`). |
| Vídeo | **mpv 0.42 (build oficial), incluido en el paquete** e incrustado en la ventana (`--wid`) | Media3/ExoPlayer no existe en escritorio, y el reproductor de Windows (Media Foundation) no abre bien MKV, HEVC 10-bit, HDR ni pistas de audio múltiples. mpv sí: HDR10, AV1, HEVC, DTS/TrueHD, subtítulos ASS/PGS, y Dolby Vision con mapeo de tonos. Va dentro de Bambi (carpeta `mpv`), no hay que instalar nada. |
| Java | **Temurin 17 JRE** dentro del paquete | Bambi se abre con doble clic sin tener Java instalado. |
| Datos | JSON en `%APPDATA%\Bambi` | Perfiles, biblioteca por perfil y ajustes. Las claves pasarán a ir cifradas con DPAPI de Windows (fase 6). |

## Atajos de teclado ↔ mando

| Mando de la tele | Teclado | Ratón |
|---|---|---|
| Cruceta | Flechas | Pasar por encima |
| OK | Intro o Espacio | Clic |
| Atrás | Escape | Botón «← Atrás» de la barra |
| Mantener OK (menú) | Supr / clic derecho (fase 3) | Clic derecho |
| Reproducir/pausa | Espacio o Intro | Clic en el vídeo o botón de la barra |
| ←/→ en el reproductor | Flechas (±10 s, como Netflix); ↑/↓ volumen; `M` silencio | Barra de tiempo, botones −10/+10, rueda = volumen |
| Omitir intro / siguiente episodio | `S` / `N` | Botones «Omitir intro» y «Siguiente episodio» |
| Temporizador de apagado | `T` | — |
| Velocidad | `[` `]` | — |
| Información del vídeo | `i` | — |
| Pistas de audio/subtítulos | `C` abre el panel (`#` y `j` siguen funcionando); columna «En internet» para buscar subtítulos | Botón CC de la barra |
| Salir del vídeo | Escape (si no está a pantalla completa) o Retroceso | Flecha «volver» arriba a la izquierda |
| Pantalla completa | F11 (en el vídeo también `F`; Escape sale) | Doble clic en el vídeo o botón de la barra |
| Teclado PIN | Números (fila o teclado numérico), Retroceso borra, Escape cancela | Teclado en pantalla |

## Cómo compilar

Desde WSL (o Linux) en la raíz del repo:

```
./gradlew :desktop:windowsZip        # _windows/Bambi y _windows/Bambi-Windows.zip
./gradlew :desktop:test              # pruebas de la capa de datos de escritorio
./gradlew :desktop:renderShots       # capturas de las pantallas sin ventana (_shots/desktop)
```

`windowsZip` baja la primera vez el JRE y el JDK 17 de Temurin (Adoptium) y mpv (GitHub de mpv-player). Con el
`jpackage.exe` de ese JDK hace `Bambi.exe` (lanzador de Windows con el icono, sin consola); el paquete queda en
`_windows/Bambi`: `Bambi.exe`, `app/` (bambi.jar), `runtime/` (Java) y `mpv/`. El JDK solo se usa para construir.

## Cómo instalar

Abre **`Bambi-Windows-Setup-<versión>.exe`** (en `_windows/`) y sigue el asistente: carpeta a elegir (por defecto
en tu usuario), accesos en el menú Inicio (grupo «Bambi») y el escritorio, y aparece en Configuración → Aplicaciones,
desde donde se desinstala. Sin permisos de administrador. Una versión nueva se instala encima de la anterior (mismo
identificador de producto); los perfiles y ajustes (`%APPDATA%\Bambi`) se conservan. Las actualizaciones se buscan
solas (Ajustes → Actualizaciones) y abren el instalador nuevo.

Se construye con `./gradlew :desktop:windowsInstaller`: jpackage (del JDK 17 de Temurin) con WiX 3.14 (zip oficial
de GitHub), a partir de la app ya preparada más mpv. Textos del instalador en español (`desktop/windows/installer`).

## Cómo configurar las claves

La primera vez se abre una **guía de 4 pasos** (bienvenida, TMDB, servicio debrid, listo), con un enlace a la
página donde se saca cada clave. Todo se guarda solo y se comprueba al momento. Trakt y AllDebrid se conectan con
un código: el botón lo copia y abre la web.

Las claves se guardan cifradas con tu usuario de Windows (`%APPDATA%\Bambi\secrets.dat`).

## Diferencias conocidas con la tele

- **Dolby Vision:** mpv lo muestra con mapeo de tonos a la pantalla (HDR o SDR), no en modo DV nativo; los monitores
  de PC no tienen DV. El perfil 7 (remux de Blu-ray) se ve con su capa base HDR10.
- **Frecuencia de refresco:** Windows no deja cambiarla por app igual que Android TV; se usará la sincronización de
  mpv con la pantalla (`video-sync=display-resample`).
- **Canal «Play Next» de Android TV:** no existe; los capítulos nuevos se avisarán con notificaciones de Windows.
- **Packs de temporada:** en escritorio todo se resuelve a través de Torrentio, que no deja elegir el archivo dentro
  del pack; por ahora solo salen capítulos sueltos (fase 4).

## Estado de las funciones

✅ hecho · 🟡 parcial · ⬜ pendiente (fase prevista) · ⚠️ no se puede igual (motivo)

### Perfiles — fase 1 ✅
- ✅ «¿Quién está viendo?» al abrir (con un solo perfil sin PIN se entra directo)
- ✅ Hasta 5 perfiles con nombre, color e icono (gatitos de Bambi + galería de Netflix)
- ✅ «Seguir viendo», vistos y progreso por perfil
- ✅ Administrar perfiles: añadir, renombrar, color, borrar con confirmación (el último no se puede borrar)
- ✅ Avatar arriba a la derecha → menú para cambiar de perfil o administrarlos
- ✅ PIN de 4 cifras por perfil (entrar y editar), mismo hash que la tele
- ✅ Perfil infantil: Inicio, Series, Películas, búsqueda y categorías solo para niños (mismas consultas que la tele)
- ✅ Migración: la primera vez, o desde una versión sin perfiles (`library.json`), se crea «Principal» con lo que hubiera
- ✅ Tema por perfil (Bambi, Océano, Bosque, Oro, Violeta, Rosa): focos, progreso, botones y barra

### Trakt — fase 2 ✅
La sincronización es **el mismo código que la tele** (`:core:trakt`, `TraktSync`); escritorio solo aporta dónde se
guardan las cosas (`DesktopTraktPlatform`) y los perfiles/biblioteca vistos como los repositorios de la tele.
- ✅ Código por perfil (Ajustes → Trakt): se copia y se abre la web de Trakt. ⚠️ Hace falta un **Client ID/Secret propio** de Trakt (gratis): la
  tele los lleva en la compilación y aquí no se copian.
- ✅ Scrobble start/pause/stop (desde el 80 % cuenta como visto)
- ✅ «Seguir viendo», vistos y Mi lista (= watchlist) sincronizados; subida inicial de lo ya visto al conectar
- ✅ Quitar de «Seguir viendo» (Supr o clic derecho) → también en Trakt
- ✅ Puntuar películas y series (1–10) en la ficha · 🟡 episodios (fase 5, desde el reproductor)
- ✅ Recomendaciones de Trakt en el Inicio
- ✅ Si Trakt cae, todo sigue funcionando en local
- ✅ Sesiones cifradas con DPAPI de Windows (`secrets.dat`)

### Inicio — fase 3 ✅
- ✅ Portada estilo Netflix con logo, edad, nota y géneros
- ✅ Pestañas Inicio · Series · Películas · Mi lista + categorías configurables por perfil (Ajustes)
- ✅ Mi lista con filtro Todo/Series/Películas + fila en Inicio
- ✅ Peticiones familiares: el infantil «Pide» desde Buscar, un adulto aprueba en Ajustes → su Mi lista
- ✅ Tendencias, Top 10 hoy (con los números grandes), filas por género
- ✅ Seguir viendo con barra + «T1:E6 · 14 min»; Supr o clic derecho para quitar
- ✅ Reproduce algo · ✅ Sorpréndeme
- ✅ Al volver de una ficha, el Inicio sigue donde estaba (posición y foco en el mismo póster)

### Fichas — fase 3 ✅
- ✅ Logo, edad, nota, duración, géneros · 🟡 etiquetas 4K/DV/HDR10 (en cada fuente; en la cabecera, fase 5)
- ✅ Reanudar / Desde el principio
- ✅ Temporadas, episodios con barra y ✓ de visto
- ✅ Marcar visto película, episodio (clic derecho) o temporada entera, también en Trakt
- ✅ Tráiler (YouTube, en el navegador) · ✅ fila de la saga
- ✅ Reparto (se oculta en Ajustes) · ✅ Mi lista · ✅ foco al volver

### Fuentes — fase 4 ✅
- ✅ Torrentio, Peerflix, TPB, Knaben, Nyaa, Comet y addons de Stremio (mismo código que la tele)
- ✅ Resultados por sección según llegan
- ✅ Orden de la tele (`PrioritizeSourcesUseCase`): caché > calidad > HDR > castellano, seeds y tamaño
- ✅ Castellano primero + botón «Solo en español»
- ✅ Anime por numeración absoluta (lo hace `:core:streams`)
- ✅ ⚡ en caché
- ✅ «Reproducir» prueba fuentes hasta que una dé vídeo: salta las no cacheadas, bloqueadas o que no son vídeo, y para
  si el servicio pide calma o la clave falla. Si mpv no arranca, pasa a la siguiente.
- ✅ «No cambiar de fuente si la elegida falla» (Ajustes)
- ✅ Fuente rota: clic derecho → Descartar; se restaura en Ajustes
- ✅ Excluir fuentes por calidad, HDR o audio (Ajustes)
- ✅ Resistencia a bloqueos: DNS cifrado (DoH) si la operadora bloquea + IPs alternativas de Cloudflare (el mismo
  `CloudflareFallbackDns` de la tele, ahora en `:core:api`)
- ✅ Todos los debrid a través de Torrentio · ✅ AllDebrid con código
- ⚠️ Packs de temporada: a través de Torrentio no se puede elegir el archivo dentro del pack; solo salen capítulos sueltos

### Reproductor — fase 5 🟡
- ✅ Dentro de la ventana con mpv (incluido): 4K, HDR10 (sale en HDR si Windows tiene el HDR activado; si no, mpv lo
  convierte), AV1, HEVC, DTS/TrueHD, subtítulos ASS/PGS
- ✅ Audio y subtítulos: todas las pistas desde la barra de mpv (`#` audio, `j` subtítulos)
- ✅ Reanudar y guardar progreso · ⚠️ «liberar decoder al minimizar» no hace falta en el PC
- ✅ Omitir intro y resumen (IntroDB): aviso «Omitir intro (S)»
- ✅ Tarjeta de siguiente episodio en los créditos (cuenta atrás, `N` para ya) y paso automático al acabar;
  desactivable en Ajustes. Al terminar una temporada sigue con la siguiente.
- ✅ Fallo al abrir → siguiente fuente · ✅ corte a mitad → se reabre en el mismo punto (una vez)
- ✅ MKV con pistas que no se pueden leer: mpv las ignora y reproduce el resto
- ✅ Temporizador de apagado: `T` cambia entre 15, 30, 45, 60, 90 min o «al terminar el episodio»
- ✅ Velocidad: `[` y `]` (0,75x–2x y más); el audio se ajusta sin cambiar el tono
- ✅ Memoria de audio y subtítulos por serie (también «sin subtítulos»)
- ✅ Exclusión de fuentes por calidad/HDR/audio (fase 4) · ✅ formato del vídeo con `i` (estadísticas de mpv)
- ✅ «Ajustar a la frecuencia de la pantalla» (Ajustes; `video-sync=display-resample`) · ⚠️ no cambia los Hz del monitor
- 🟡 «Castellano primero» elige la pista en castellano si la hay; aún no cambia de fuente si no la hay
- ⬜ Miniaturas al buscar en la barra (mpv no las trae; habría que añadir un script como thumbfast)
- ⬜ Subtítulos externos (OpenSubtitles, Podnapisi, SubDL) con sincronización automática
- ⚠️ Dolby Vision: con mapeo de tonos (ver diferencias)

### Buscar — fase 6 ✅
- ✅ Teclado en pantalla (ABC o QWERTY, con Ñ) + teclado del PC · ✅ resultados al momento · ✅ «Lo más visto hoy» sin escribir

### Ajustes y guía — fase 6 ✅
- ✅ Guía de primer arranque (4 pasos) con enlace a la página de cada clave y comprobación al momento
- ⚠️ Sin QR ni «configurar desde el móvil»: en el PC todo se escribe o se pega directamente (decisión de diseño)
- ✅ Claves cifradas con DPAPI de Windows (`secrets.dat`); las de versiones anteriores se migran solas y desaparecen
  de `config.json`
- ✅ Comprobar claves (TMDB y debrid) con motivo · ✅ guardado automático
- ✅ Activar/desactivar: castellano primero, siguiente episodio, reparto, frecuencia de pantalla, no cambiar de fuente
- ✅ Idioma del contenido (español/inglés: títulos, sinopsis y audio preferido) · ⚠️ los menús siguen en español
- ✅ Addons de Stremio: añadir, probar y quitar, con nombre y logo; avisa si el addon exige configurarse antes
- ✅ Las URL con claves no salen en ningún registro (el de mpv se borra al acabar cada vídeo)
- ⚠️ «Búsqueda con miniaturas o saltos de 10 s» y «búfer ajustable»: mpv usa sus saltos (5 s / 1 min) y su propia caché

### Estrenos y actualizaciones — fase 7 ✅
- ✅ Centro de estrenos (pestaña «Estrenos»): capítulos de los últimos 30 días sin ver y de los próximos 30, de las
  series que sigues; contador en la barra de arriba
- ✅ Avisos de Windows (bandeja) de capítulos nuevos, una vez por capítulo, cada 6 horas (equivale al canal de la tele)
- ✅ Buscar actualizaciones en las releases de GitHub al abrir y cada 6 horas; se instala solo al pulsar (MSI con el
  instalador de Windows o zip con un script que lo coloca al cerrar Bambi)

### Windows — fase 8
- ✅ `Bambi.exe` (jpackage) sin .bat; «Instalar en este PC» desde Ajustes: copia a tu usuario, accesos en menú Inicio
  y escritorio con el icono, entrada en Aplicaciones de Windows; desinstalar conserva perfiles y ajustes
- ✅ Mando XInput (Xbox y compatibles): cruceta/stick, A, B, Start, X (omitir intro), Y (siguiente), LB/RB
- ✅ Modo ventana y pantalla completa «10 pies» con F11; navegación por foco con flechas
- ✅ La ventana vuelve con su tamaño, posición, monitor y modo · ✅ escalado de Windows (DPI) respetado
- ✅ Icono, intro de Bambi y tema oscuro como la tele
- ⚠️ MSI: la configuración está (`./gradlew :desktop:packageMsi`), pero hay que generarlo **en Windows** (jpackage +
  WiX); desde WSL no se puede. ⚠️ Firmado: necesita un certificado de firma de código (de pago).

## Pruebas

`./gradlew :desktop:test`: perfiles y migración, Trakt (claves, progreso, Mi lista, preferencias, secretos), Inicio y
fichas (etiquetas, marcar visto, peticiones, categorías, caché) y fuentes (resolución de enlaces, auto-prueba,
descartadas, exclusiones). La app de la tele sigue pasando sus pruebas y lint con los módulos compartidos.

## Licencia y créditos

GPL-3.0, como Bambi. Usa datos de TMDB (no avalado por TMDB), Torrentio, Trakt e IntroDB; ideas y lógica de NuvioTV y
Franklix (GPL-3); vídeo con mpv (GPL) y FFmpeg.
