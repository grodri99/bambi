# Changelog — Bambi para Windows

## [Sin publicar]

### Rediseño
- Interfaz más premium: nueva paleta, letra de Windows 11, barra con pestañas de texto, pósteres con sombra, botones
  de cristal, Top 10 con números huecos, panel de fuentes de cristal con 4K en dorado y Ajustes por categorías.
- Fuera los QR y «configurar desde el móvil»: en el PC todo se pega a mano; Trakt y AllDebrid copian el código y
  abren la web.
- La interfaz escala con la ventana (en 1440p y 4K se ve a su tamaño, no diminuta); logo de Bambi, títulos y
  sinopsis más grandes; línea de datos con la edad, la nota en dorado y los géneros.
- Los logos oscuros de TMDB (p. ej. «La Odisea») se pintan en blanco para que se lean sobre el fondo.
- La barra de arriba se vuelve sólida al bajar por las filas; los géneros de series salen en español; Ajustes con
  ancho máximo para que los campos no ocupen toda la pantalla.
- Reproductor con controles como Netflix en el PC: volver arriba; abajo la barra de tiempo (con lo que queda y la
  hora al pasar el ratón), reproducir/pausa, −10 s, +10 s, volumen, título, siguiente episodio, audio y subtítulos
  y pantalla completa. Salen al mover el ratón y se esconden a los 3 s. «Omitir intro» y «Siguiente episodio» son
  botones.
- El vídeo ya no se pone solo a pantalla completa: F, F11, doble clic o el botón. Escape sale de pantalla completa
  y, si no lo está, vuelve a la ficha. Arreglado el vídeo que se quedaba pequeño en una esquina con la interfaz
  escalada.
- Los controles vuelven a salir al mover el ratón o hacer clic (el ratón ahora llega al reproductor); el puntero se
  esconde a los 3 s.
- Ficha: las fuentes ya no están siempre abiertas a la derecha. Botón «Fuentes» (como en la tele) que abre un panel
  lateral; cada buscador (Torrentio, Comet, Peerflix…) es un desplegable, todos cerrados de primeras, con cuántas
  hay en caché y la mejor calidad. En series, clic en un episodio lo reproduce; clic derecho → «Ver fuentes».
- Adiós a los .bat: `Bambi.exe` de verdad (icono de Bambi, sin consola) y «Instalar en este PC» en Ajustes → Bambi
  (menú Inicio, escritorio y Aplicaciones de Windows, desde donde se desinstala).
- Arreglado el botón de pantalla completa de la barra (la petición llegaba entre comillas y se repetía).
- «Ocultar lo que aún no se puede ver en España» (Ajustes → Fuentes, apagado de serie): el Inicio, el Top 10 y las
  categorías quitan las películas que aún no han salido en digital en España (fechas de TMDB; si solo hay estreno en
  cines, se dan por disponibles a los tres meses). Las series y la búsqueda no se filtran.
- AllDebrid: arreglado «Expected start of the array» al reproducir (AllDebrid devuelve el estado de un magnet como
  objeto suelto; vale también para la tele).
- Ajustes rediseñados: tu perfil y las secciones con icono a la izquierda; cada sección con su cabecera y grupos
  de filas (icono, explicación y el control a la derecha), como los ajustes de Windows 11 o macOS. Las claves se
  ven tapadas («•••• a3f9») con su estado (Correcta / No vale) y se cambian con «Cambiar».
- Las filas del Inicio y de las fichas se arrastran con el ratón (clic mantenido, con inercia) y tienen flechas ‹ ›
  en los bordes al pasar por encima, como en Netflix. Con el ratón ya no se queda marcado el póster del último clic.
- Subtítulos externos: en el reproductor, CC (o C) → «En internet» busca en OpenSubtitles y Podnapisi (sin cuenta)
  y pone el elegido. Opción en Ajustes → Reproducción para ponerlos solos si el audio no está en español, y tamaño
  de los subtítulos. Subtítulos con letra de Windows, contorno y sombra; suben cuando se ve la barra.
- Editor de subtítulos en el reproductor, como en la tele (CC → «Editar subtítulos…» o la tecla E): sincronía en
  pasos de 0,1 s, tamaño, posición, letra, color, fondo, borde y estilo de los .ass, en vivo y con el vídeo en pausa
  sobre una frase de muestra. El estilo se guarda para los siguientes vídeos (la sincronía es de cada vídeo).
- Arreglado: al entrar en un vídeo, Escape (y el resto de teclas) no llegaban y la app parecía colgada. Ahora el
  teclado del reproductor se recoge para toda la ventana, nada que hable con mpv para la interfaz, y Escape cierra
  por capas: el editor o el panel, luego la pantalla completa y luego el vídeo (como Netflix). Escape mantenido
  cuenta una vez y el Escape que cierra el vídeo ya no cierra también la ficha.
- Arreglado: las teclas pulsadas seguidas podían llegar desordenadas a mpv (ahora van en orden); al saltar de un
  vídeo a otro nunca quedan dos mpv sonando; un mpv que arrancaba tarde ya no se «queda» con el teclado; no se
  reintenta otra fuente si ya saliste del vídeo; no se apila dos veces la misma pantalla.
- Instalador de verdad: `Bambi-Windows-Setup-<versión>.exe`, con asistente en español, carpeta a elegir, accesos en
  el menú Inicio y el escritorio y desinstalación desde Configuración → Aplicaciones (sin administrador). Las
  versiones nuevas se instalan encima y las actualizaciones abren el instalador nuevo. Fuera «Instalar en este PC».

### Fase 8 — Windows
- Instalador para el usuario (accesos en menú Inicio y escritorio) y desinstalador; icono de Bambi.
- Mando XInput, pantalla completa con F11 y la ventana vuelve como se cerró (tamaño, posición, monitor).

### Fase 7 — Estrenos y actualizaciones
- Pestaña «Estrenos» con contador, avisos de Windows de capítulos nuevos y actualizaciones desde GitHub.

### Fase 6 — Buscar, Ajustes y guía
- Guía de primer arranque, claves cifradas con DPAPI (con
  migración), gestor de addons con nombre, logo y aviso de «hay que configurarlo», idioma del contenido y teclado en
  pantalla.

### Fase 5 — Reproductor (parcial)
- Omitir intro y resumen (IntroDB), tarjeta de siguiente episodio en los créditos y paso automático (desactivable).
- Temporizador de apagado (T), memoria de audio y subtítulos por serie, HDR de Windows, ajuste a la frecuencia de la
  pantalla y reapertura en el mismo punto si se corta.

### Fase 4 — Fuentes
- Orden de fuentes igual que la tele, castellano primero y botón «Solo en español».
- «Reproducir» prueba fuentes hasta que una dé vídeo (salta las no cacheadas, bloqueadas o que no son vídeo; para si
  el servicio pide calma) y, si mpv no arranca, pasa a la siguiente. Opción «No cambiar de fuente si la elegida falla».
- Descartar fuentes rotas (clic derecho) y restaurarlas en Ajustes. Excluir por calidad, HDR o audio.
- Resistencia a bloqueos: DNS cifrado (DoH) y las IPs alternativas de Cloudflare de la tele.
- AllDebrid con código o QR.

### Fase 3 — Inicio y fichas
- Portada con edad y nota; Top 10 hoy; categorías configurables por perfil; «Reproduce algo» y «Sorpréndeme».
- «Seguir viendo» con «T1:E6 · 14 min»; quitar con Supr o clic derecho.
- Mi lista con filtro Todo/Series/Películas.
- Fichas: «Desde el principio», tráiler, marcar visto (película, episodio, temporada), reparto (ocultable) y saga.
- Peticiones familiares entre el perfil infantil y los adultos.
- Al volver de una ficha, el Inicio conserva la posición y el foco.

### Fase 2 — Trakt
- Conectar por perfil con código y QR; scrobble, «Seguir viendo», vistos, Mi lista = watchlist, puntuaciones y
  recomendaciones, con la misma sincronización que la tele (`:core:trakt`). Sesiones cifradas con DPAPI.

### Fase 1 — Perfiles
- PIN de 4 cifras, perfil infantil, tema por perfil, menú de perfiles en el avatar, borrar con confirmación, máximo 5
  y migración a «Principal».

### Antes de las fases
- Catálogo de TMDB, fichas, búsqueda de fuentes compartida con la tele, reproductor mpv incluido e incrustado en la
  ventana, intro de Bambi, comprobación de claves y guardado automático de Ajustes.
