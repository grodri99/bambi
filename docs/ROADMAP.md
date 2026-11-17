# Hoja de ruta de Bambi

> Documento vivo. Última revisión: 16 de noviembre de 2026.
> Inspiración funcional: Jellyfin, Emby y el resto de servidores domésticos, adaptada a lo que Bambi ya es
> (cliente Android TV con TMDB, Trakt, debrid y addons de Stremio), no a un servidor con biblioteca propia.

> Traspaso técnico para otro agente: [HANDOFF-OPUS.md](HANDOFF-OPUS.md).

## Leyenda

- **Aprobado** — entra en el plan y se puede empezar.
- **Pendiente** — buena idea, sin fecha; se decide más adelante.
- **En curso** — se está trabajando ahora.
- **Hecho** — ya está en la app.
- **Parcial** — hay una parte funcionando; falta el resto.
- ⭐ — marcada como favorita por Gonza.

## Prioridad propuesta

- **P0** — primero, impacto alto y alcance pequeño/medio.
- **P1** — siguiente bloque.
- **P2** — cuando lo anterior esté cerrado y probado en la tele.

---

## Reproducción

| Función | Estado | Prioridad | Notas |
| --- | --- | --- | --- |
| Temporizador de apagado («duérmete en 30 min») | **Hecho** ⭐ | P0 | En Reproducción: desactivado → 15 → 30 → 45 → 60 → 90 → fin del episodio. Al vencer pausa, avisa y vuelve a Inicio; con «fin del episodio» no encadena el siguiente. |
| Saltar resumen / «anteriormente en…» y créditos automáticamente | **Parcial** | P1 | Intro y «Anteriormente en…» ya se saltan solos (ajuste). Los créditos se resuelven con el aviso de siguiente episodio; falta saltarlos como tales. |
| Velocidad de reproducción (documentales a 1.25x) | **Hecho** | P1 | 0.75x–2x desde el mando. Con audio directo Dolby/DTS se decodifica a PCM (FFmpeg para DTS/TrueHD) y al volver a 1x recupera el audio directo; con AAC no hay corte. Los subtítulos (internos y externos) siguen la velocidad porque van por la posición del vídeo. Falta recordarla por perfil. |
| Descargas para ver sin conexión | **Hecho** | P1 | Más → Descargar en la ficha; pestaña Descargas; carpeta (interna o USB) en Ajustes. Se guarda en la carpeta de la app del disco elegido (se borra al desinstalar). |
| Recordar pista de audio y subtítulos por serie | **Hecho** | P1 | Al elegir audio o subtítulos a mano se guarda por perfil y título (incluido «subtítulos off»); se reaplica en el siguiente episodio. |
| «Reproduce algo»: episodio aleatorio / continuar lista | **Hecho** ⭐ | P1 | Botón en Inicio (junto a Sorpréndeme). Prioriza: A continuación → Estrenos → Seguir viendo → Mi lista → Inicio. |

## Descubrir contenido

| Función | Estado | Prioridad | Notas |
| --- | --- | --- | --- |
| Páginas de persona (actor/director) con filmografía reproducible | **Hecho** ⭐ | P1 | Ficha con foto, biografía y títulos; cada título abre la ficha normal y resuelve fuentes como siempre. |
| Tráilers desde YouTube en la ficha | **Hecho** | P2 | Botón Tráiler en la ficha; abre el vídeo de TMDB en YouTube. |
| Notas juntas (IMDb, TMDB, Rotten Tomatoes) | **Pendiente** | P2 | IMDb/RT no tienen API pública estable; valorar OMDb o scraping y cachear. |
| Colecciones y sagas automáticas (TMDB) | **Hecho** | P1 | Fila de saga en la ficha con el resto de películas de la colección de TMDB. |
| «Sorpréndeme»: película al azar según gustos | **Hecho** | P2 | Título al azar del Inicio con buena nota, sin «Seguir viendo» ni «Mi lista». Aún no usa Trakt. |

## Hogar y perfiles

| Función | Estado | Prioridad | Notas |
| --- | --- | --- | --- |
| Peticiones estilo Overseerr (perfil pide, admin aprueba) | **Hecho** ⭐ | P1 | Solo los perfiles infantiles piden: en Buscar, lo que no es infantil sale bloqueado en «Pídeselo a un adulto». Cualquier perfil adulto aprueba en Ajustes → Peticiones familiares: se desbloquea para ese niño y entra en su Mi lista. |
| Inicio personalizable por perfil (filas y orden) | **Pendiente** | P2 | Encaja con la configuración desde el móvil que ya existe; persistir orden por perfil. |
| Temas/estética por perfil | **Hecho** ⭐ | P1 | Cada perfil elige acento (Bambi, Océano, Bosque, Oro, Violeta, Rosa). Se aplica a progreso, focos, indicadores, reproductor y ficha; persiste y cambia al seleccionar perfil. |

## Fiabilidad

| Función | Estado | Prioridad | Notas |
| --- | --- | --- | --- |
| Varias cuentas de debrid con relevo | **Pendiente** | P1 | Si una falla o se queda sin tráfico, pasar a la siguiente. Debe ser transparente para la reproducción y no filtrar claves en logs. |
| Marcar fuente rota | **Hecho** | P1 | Mantén OK en una fuente → «Marcar fuente como rota». Se marca por hash (el mismo torrent se oculta en todos los buscadores) y caduca a los 60 días. Ajustes → Fuentes rotas permite restaurarlas. |
| Modo salvapantallas con fondos en movimiento | **Pendiente** | P2 | Respetar el modo ligero y las reglas de rendimiento de la tele; nada de zooms/crossfades continuos sin medir. |

---

## Orden de ataque recomendado

1. **Varias cuentas de debrid con relevo** (P1).
2. P2: Inicio personalizable y modo salvapantallas.

Hecho ya: temporizador de apagado, velocidad de reproducción, páginas de persona, «Reproduce algo», «Sorpréndeme»,
temas por perfil, tráilers, sagas, marcar fuente rota, peticiones familiares y memoria de audio/subtítulos por serie.

## Reglas para cada función

- Una rama/commit por función y una compilación de prueba por cambio medible.
- Texto de interfaz en español e inglés (`tr("Español", "English")`).
- Probar en el Nokia Streaming Box 8010: D-pad, volver, foco y reproducción real.
- Nada de claves en logs, capturas, tests ni documentación.
- Si una función toca reproducción, mantener el camino único de resolución (`SelectedContentHolder` → Details → player).

## Referencias

- Jellyfin: <https://github.com/jellyfin/jellyfin>
- Emby: <https://emby.media/>
- Overseerr: <https://github.com/sct/overseerr>
