package com.shieldstream.tv.desktop

import com.shieldstream.tv.domain.SkipSegments
import java.io.File
import java.util.Locale

/**
 * Lo que el reproductor de la tele hace por encima del vídeo, para mpv: los controles al estilo Netflix (barra de
 * tiempo, ±10 s, volumen, audio y subtítulos, pantalla completa), omitir intro y resumen (IntroDB), tarjeta de
 * siguiente episodio en los créditos, temporizador de apagado y, al salir, qué audio y subtítulos llevabas (para
 * recordarlos en esa serie). Es un script de mpv (Lua) que Bambi escribe junto a cada reproducción; no se descarga nada.
 */
object BambiScript {
    /** Opciones del script para esta reproducción (--script-opts). */
    fun options(
        segments: SkipSegments?,
        nextEpisode: Boolean,
        slateCheck: Boolean = false,
        accent: String = "E50914",
        autoSubs: Boolean = false,
        subStyle: String = "",
    ): List<String> {
        fun range(r: com.shieldstream.tv.domain.TimeRange?) =
            r?.let { "%.1f-%.1f".format(Locale.US, it.startMs / 1000.0, if (it.endMs == Long.MAX_VALUE) -1.0 else it.endMs / 1000.0) }.orEmpty()
        return listOf(
            "bambi-intro=${range(segments?.intro)}",
            "bambi-recap=${range(segments?.recap)}",
            "bambi-outro=${range(segments?.outro)}",
            "bambi-next=${if (nextEpisode) "yes" else "no"}",
            "bambi-slate=${if (slateCheck) "yes" else "no"}",
            "bambi-accent=$accent",
            "bambi-autosubs=${if (autoSubs) "yes" else "no"}",
            "bambi-substyle=$subStyle",
            "bambi-lang=${if (com.shieldstream.tv.model.Lang.isEnglish) "en" else "es"}",
        )
    }

    fun write(dir: File): File = File(dir, "bambi.lua").apply { writeText(LUA) }

    /** Lo que el script cuenta al salir (en la salida de mpv). */
    data class Report(
        val audioLang: String?,
        val subLang: String?,
        val next: Boolean,
        val sleep: Boolean,
        val sleepAtEnd: Boolean,
        /** Era un vídeo de aviso del addon («torrent inactivo»…), no la película. */
        val slate: Boolean = false,
    )

    fun parse(text: String): Report {
        fun last(key: String) = Regex("$key=([^\\s]*)").findAll(text).lastOrNull()?.groupValues?.get(1)
        return Report(
            audioLang = last("bambi-alang")?.takeIf { it.isNotBlank() },
            subLang = last("bambi-slang")?.takeIf { it.isNotBlank() },
            next = "bambi-next-now" in text,
            sleep = "bambi-sleep-now" in text,
            sleepAtEnd = "bambi-sleep-episode" in text,
            slate = "bambi-slate-now" in text,
        )
    }

    private val LUA = """
local mp = require 'mp'
local options = require 'mp.options'
local o = { intro = "", recap = "", outro = "", next = "no", slate = "no", accent = "E50914", lang = "es", autosubs = "no", substyle = "" }
options.read_options(o, "bambi")

-- Controles de Bambi encima del vídeo, como los de Netflix en el PC: arriba «volver»; abajo la barra de tiempo
-- (con lo que queda), reproducir/pausa, -10 s, +10 s, volumen, el título, siguiente episodio, audio y subtítulos y
-- pantalla completa. Aparecen al mover el ratón o pulsar una tecla y se esconden a los 3 s (en pausa se quedan).

local EN = o.lang == "en"
local function tr(es, en) if EN then return en end return es end

local function range(s)
  local a, b = s:match("^([%d%.]+)%-(%-?[%d%.]+)$")
  if not a then return nil end
  return { tonumber(a), tonumber(b) }
end
local skips = {}
for _, k in ipairs({ "recap", "intro" }) do
  local r = range(o[k])
  if r then table.insert(skips, { name = k, s = r[1], e = r[2] }) end
end
local outro = range(o.outro)

local function bgr(hex)
  if not hex:match("^%x%x%x%x%x%x$") then hex = "E50914" end
  return hex:sub(5, 6) .. hex:sub(3, 4) .. hex:sub(1, 2)
end
local ACCENT = bgr(o.accent)
local WHITE, BLACK = "FFFFFF", "000000"
local FONT = "Segoe UI"
local ICONFONT = "Segoe MDL2 Assets"

local function utf8(cp)
  if cp < 0x80 then return string.char(cp) end
  if cp < 0x800 then return string.char(0xC0 + math.floor(cp / 0x40), 0x80 + cp % 0x40) end
  return string.char(0xE0 + math.floor(cp / 0x1000), 0x80 + math.floor(cp / 0x40) % 0x40, 0x80 + cp % 0x40)
end
local ICON = {
  play = utf8(0xE768), pause = utf8(0xE769), back = utf8(0xE72B), vol = utf8(0xE767), mute = utf8(0xE74F),
  full = utf8(0xE740), window = utf8(0xE73F), cc = utf8(0xE7F0), nextep = utf8(0xE893),
  rew = utf8(0xE777), fwd = utf8(0xE72C), check = utf8(0xE73E),
}

local function esc(s)
  s = tostring(s or "")
  s = s:gsub("\\", "\\\239\187\191")
  s = s:gsub("{", "\\{")
  s = s:gsub("}", "\\}")
  s = s:gsub("[\r\n]", " ")
  return s
end

-- Corta un texto UTF-8 sin partir letras.
local function cut(s, n)
  if #s <= n then return s end
  local i = n
  while i > 1 and s:byte(i) >= 0x80 and s:byte(i) < 0xC0 do i = i - 1 end
  return s:sub(1, i - 1) .. "…"
end

local function r(v) return math.floor(v + 0.5) end

local function fmt(t)
  t = math.max(0, math.floor(t or 0))
  local h, m, s = math.floor(t / 3600), math.floor(t / 60) % 60, t % 60
  if h > 0 then return string.format("%d:%02d:%02d", h, m, s) end
  return string.format("%d:%02d", m, s)
end

-- Dibujo (ASS) -------------------------------------------------------------------------------------------------
local function shape(path, color, alpha, extra)
  return string.format("{\\an7\\pos(0,0)\\bord0\\shad0\\1c&H%s&\\1a&H%02X&%s\\p1}%s{\\p0}", color, alpha, extra or "", path)
end

local function rect(x, y, w, h, color, alpha)
  x, y, w, h = r(x), r(y), r(w), r(h)
  return shape(string.format("m %d %d l %d %d l %d %d l %d %d", x, y, x + w, y, x + w, y + h, x, y + h), color, alpha)
end

local function rrectPath(x, y, w, h, rad)
  rad = math.min(rad, w / 2, h / 2)
  local c = rad * 0.448
  local function p(...) local t = {} for i, v in ipairs({ ... }) do t[i] = r(v) end return table.concat(t, " ") end
  return "m " .. p(x + rad, y) ..
    " l " .. p(x + w - rad, y) .. " b " .. p(x + w - c, y, x + w, y + c, x + w, y + rad) ..
    " l " .. p(x + w, y + h - rad) .. " b " .. p(x + w, y + h - c, x + w - c, y + h, x + w - rad, y + h) ..
    " l " .. p(x + rad, y + h) .. " b " .. p(x + c, y + h, x, y + h - c, x, y + h - rad) ..
    " l " .. p(x, y + rad) .. " b " .. p(x, y + c, x + c, y, x + rad, y)
end

local function rrect(x, y, w, h, rad, color, alpha, border, bcolor)
  local extra = ""
  if border then extra = string.format("\\bord%.1f\\3c&H%s&\\3a&H00&", border, bcolor or WHITE) end
  return shape(rrectPath(x, y, w, h, rad), color, alpha, extra)
end

local function circle(cx, cy, rad, color, alpha)
  return shape(rrectPath(cx - rad, cy - rad, rad * 2, rad * 2, rad), color, alpha)
end

local function text(x, y, an, size, s, color, alpha, bold, font)
  return string.format("{\\an%d\\pos(%d,%d)\\fn%s\\fs%d\\b%d\\bord0\\shad0\\1c&H%s&\\1a&H%02X&\\q2}%s",
    an, r(x), r(y), font or FONT, r(size), bold and 1 or 0, color or WHITE, alpha or 0, s)
end

-- Estado ----------------------------------------------------------------------------------------------------------
local ov = mp.create_osd_overlay("ass-events")
local W, H = 0, 0
local visible, hideAt = false, 0
local panel = false
local buttons = {}
local mouse = { x = -1, y = -1, inside = false }
local drag, volumeDrag = nil, false
local bar = { x1 = 0, x2 = 1, y = 0 }
local vol = { x1 = 0, x2 = 1 }
local volumeOpen = false
local current = nil
local counting, nextAt, dismissed = false, nil, false
local atEnd = false
local pendingClick = nil
local toast, toastUntil = nil, 0
local lastData, lastW, lastH = nil, 0, 0
local reqn = 0

local subMargin = 46
local lastUi = 0
local baseMargin = 46
local function now() return mp.get_time() end
local function scale() return math.max(0.55, math.min(2.2, H / 1080)) end

local function show(seconds)
  visible = true
  hideAt = math.max(hideAt, now() + (seconds or 3))
end

local function say(s)
  toast, toastUntil = s, now() + 2
end

-- Peticiones a Bambi (pantalla completa, Escape): Bambi escucha esta propiedad por la tubería de control.
local function request(what)
  mp.msg.info("bambi-request " .. what .. " raton=" .. math.floor(mouse.x) .. "," .. math.floor(mouse.y) .. " osd=" .. W .. "x" .. H)
  reqn = reqn + 1
  mp.set_property("user-data/bambi/request", what .. "#" .. reqn)
end
local function bambiListening() return mp.get_property_native("user-data/bambi/fs") ~= nil end
local function isFullscreen() return mp.get_property_native("user-data/bambi/fs") == true end

local function inside(x1, y1, x2, y2)
  return mouse.inside and mouse.x >= x1 and mouse.x <= x2 and mouse.y >= y1 and mouse.y <= y2
end

local function addButton(x1, y1, x2, y2, fn, kind)
  buttons[#buttons + 1] = { x1, y1, x2, y2, fn = fn, kind = kind }
end

local function hit()
  for i = #buttons, 1, -1 do
    local b = buttons[i]
    if mouse.x >= b[1] and mouse.x <= b[3] and mouse.y >= b[2] and mouse.y <= b[4] then return b end
  end
  return nil
end

-- Acciones ----------------------------------------------------------------------------------------------------------
local function togglePause() mp.command("cycle pause"); show() end
local function seekBy(s) mp.commandv("seek", tostring(s), "relative"); show() end
local function changeVolume(d)
  local v = math.max(0, math.min(100, (mp.get_property_number("volume") or 100) + d))
  mp.set_property_number("volume", v)
  mp.set_property_bool("mute", false)
  show()
end

local function skip()
  if current then
    local e = current.e
    if e < 0 then e = (mp.get_property_number("duration") or 0) - 1 end
    mp.commandv("seek", tostring(e), "absolute")
    current = nil
  end
end

local function goNext()
  print("bambi-next-now")
  mp.command("quit")
end

local function goBack() mp.command("quit") end

-- Pistas -------------------------------------------------------------------------------------------------------------
local LANG = {
  spa = "Español", es = "Español", esp = "Español", eng = "Inglés", en = "Inglés", fre = "Francés", fra = "Francés",
  fr = "Francés", ger = "Alemán", deu = "Alemán", de = "Alemán", ita = "Italiano", it = "Italiano", por = "Portugués",
  pt = "Portugués", jpn = "Japonés", ja = "Japonés", rus = "Ruso", ru = "Ruso", kor = "Coreano", ko = "Coreano",
  chi = "Chino", zho = "Chino", zh = "Chino", cat = "Catalán", ca = "Catalán", pol = "Polaco", pl = "Polaco",
  dut = "Neerlandés", nld = "Neerlandés", nl = "Neerlandés", tur = "Turco", tr = "Turco", ara = "Árabe", ar = "Árabe",
  hin = "Hindi", hi = "Hindi", swe = "Sueco", sv = "Sueco",
  cze = "Checo", ces = "Checo", cs = "Checo", dan = "Danés", da = "Danés", fin = "Finés", fi = "Finés",
  nor = "Noruego", nob = "Noruego", no = "Noruego", gre = "Griego", ell = "Griego", el = "Griego",
  heb = "Hebreo", he = "Hebreo", hun = "Húngaro", hu = "Húngaro", rum = "Rumano", ron = "Rumano", ro = "Rumano",
  ukr = "Ucraniano", uk = "Ucraniano", tha = "Tailandés", th = "Tailandés", vie = "Vietnamita", vi = "Vietnamita",
  ind = "Indonesio", id = "Indonesio", may = "Malayo", msa = "Malayo", ms = "Malayo", bul = "Búlgaro", bg = "Búlgaro",
  hrv = "Croata", hr = "Croata", slv = "Esloveno", sl = "Esloveno", srp = "Serbio", sr = "Serbio",
  ["es-es"] = "Español (España)", ["es-419"] = "Español (Latino)", ["es-la"] = "Español (Latino)",
  ["es-mx"] = "Español (Latino)", ["pt-br"] = "Portugués (Brasil)", ["pt-pt"] = "Portugués", ["en-us"] = "Inglés",
  ["en-gb"] = "Inglés", spl = "Español (Latino)",
}
local LANG_EN = { spa = "Spanish", es = "Spanish", esp = "Spanish", eng = "English", en = "English" }

local function trackLabel(t)
  local code = (t.lang or ""):lower()
  local name = (EN and LANG_EN[code]) or LANG[code] or (t.lang and t.lang:upper()) or (tr("Pista ", "Track ") .. t.id)
  local title = (t.title or ""):gsub("%c", "")
  local low = title:lower()
  if title ~= "" and low ~= name:lower() then
    -- «Español Forzados» ya dice el idioma: se usa tal cual.
    if low:find(name:lower(), 1, true) then name = cut(title, 34) else name = name .. " · " .. cut(title, 26) end
  end
  if t.forced and not low:find("forz", 1, true) and not low:find("forced", 1, true) then name = name .. tr(" (forzados)", " (forced)") end
  return name
end

local function tracks(kind)
  local list = {}
  for _, t in ipairs(mp.get_property_native("track-list") or {}) do
    if t.type == kind then list[#list + 1] = t end
  end
  return list
end

-- Dibujo de los controles -------------------------------------------------------------------------------------------
local function gradient(out, y, h, a1, a2)
  local bands = 24
  for i = 0, bands - 1 do
    local a = a1 + (a2 - a1) * (i + 0.5) / bands
    local top, bottom = r(y + h * i / bands), r(y + h * (i + 1) / bands)
    out[#out + 1] = rect(0, top, W, bottom - top, BLACK, math.floor(a))
  end
end

local function iconButton(out, cx, cy, size, glyph, fn, label)
  local half = size * 0.95
  local hot = inside(cx - half, cy - half, cx + half, cy + half)
  local sc = hot and 120 or 100
  out[#out + 1] = string.format("{\\an5\\pos(%d,%d)\\fn%s\\fs%d\\fscx%d\\fscy%d\\bord0\\shad0\\1c&HFFFFFF&\\1a&H00&}%s",
    r(cx), r(cy), ICONFONT, r(size), sc, sc, glyph)
  if label then
    out[#out + 1] = string.format("{\\an5\\pos(%d,%d)\\fn%s\\fs%d\\fscx%d\\fscy%d\\b1\\bord0\\shad0\\1c&HFFFFFF&}%s",
      r(cx), r(cy + size * 0.03), FONT, r(size * 0.36), sc, sc, label)
  end
  addButton(cx - half, cy - half, cx + half, cy + half, fn)
end

local function pill(out, x, y, w, h, label, primary, fn)
  local s = scale()
  local hot = inside(x, y, x + w, y + h)
  if primary then
    out[#out + 1] = rrect(x, y, w, h, 6 * s, WHITE, hot and 0x00 or 0x10)
    out[#out + 1] = text(x + w / 2, y + h / 2, 5, 22 * s, label, BLACK, 0, true)
  else
    out[#out + 1] = rrect(x, y, w, h, 6 * s, hot and WHITE or BLACK, hot and 0x00 or 0x50, 2 * s, WHITE)
    out[#out + 1] = text(x + w / 2, y + h / 2, 5, 22 * s, label, hot and BLACK or WHITE, 0, true)
  end
  addButton(x, y, x + w, y + h, fn)
end

-- Editor de subtítulos (como en la tele): sincronía, tamaño, posición, letra, color, fondo y borde, en vivo.
local FONTS = {
  { tr("Normal", "Normal"), "Segoe UI Semibold" }, { "Serif", "Georgia" }, { tr("Monoespaciada", "Monospace"), "Consolas" },
  { tr("Estrecha", "Condensed"), "Bahnschrift SemiCondensed" }, { tr("Negrita", "Bold"), "Segoe UI Black" },
}
local COLORS = {
  { tr("Blanco", "White"), "#FFFFFF" }, { tr("Amarillo", "Yellow"), "#FFF200" }, { tr("Verde", "Green"), "#46D369" },
  { tr("Cian", "Cyan"), "#75DFFF" }, { tr("Rojo", "Red"), "#FF5A5F" },
}
local BACKS = { { tr("Ninguno", "None"), nil }, { tr("Sombreado", "Shaded"), "#A6000000" }, { tr("Negro", "Black"), "#FF000000" } }
local EDGES = { tr("Contorno", "Outline"), tr("Sombra", "Shadow"), tr("Ninguno", "None") }

local DEFAULT_STYLE = { size = 100, pos = 6, font = 1, color = 1, back = 1, edge = 1, original = true }
local function copyStyle(t) local c = {} for k, v in pairs(t) do c[k] = v end return c end
local function parseStyle(s)
  local v = {}
  for n in (s or ""):gmatch("[^:]+") do v[#v + 1] = tonumber(n) end
  if #v < 7 then return nil end
  local function idx(i, n) return math.max(1, math.min(n, (v[i] or 0) + 1)) end
  return { size = v[1], pos = v[2], font = idx(3, 5), color = idx(4, 5), back = idx(5, 3), edge = idx(6, 3), original = v[7] == 1 }
end
local style = parseStyle(o.substyle) or copyStyle(DEFAULT_STYLE)
local function encodeStyle()
  return table.concat({ style.size, style.pos, style.font - 1, style.color - 1, style.back - 1, style.edge - 1, style.original and 1 or 0 }, ":")
end

local function applyStyle()
  mp.set_property_number("sub-scale", style.size / 100)
  baseMargin = math.floor(720 * style.pos / 100 + 0.5)
  subMargin = nil
  mp.set_property("sub-font", FONTS[style.font][2])
  mp.set_property("sub-color", COLORS[style.color][2])
  local back = BACKS[style.back][2]
  if back then
    mp.set_property("sub-border-style", "background-box")
    mp.set_property("sub-back-color", back)
  else
    mp.set_property("sub-border-style", "outline-and-shadow")
  end
  local outline, shadow = 2.2, 1.2
  if style.edge == 2 then outline, shadow = 0, 2.5 elseif style.edge == 3 then outline, shadow = 0, 0 end
  if back then outline = 4 end
  mp.set_property_number("sub-outline-size", outline)
  mp.set_property_number("sub-shadow-offset", shadow)
  mp.set_property("sub-ass-override", style.original and "scale" or "force")
end

-- Cada cambio se guarda en Bambi para los próximos vídeos (la sincronía no: es de este vídeo).
local function styleChanged()
  applyStyle()
  if bambiListening() then request("st:" .. encodeStyle()) end
end

local function clampStep(value, delta, min, max) return math.max(min, math.min(max, value + delta)) end
local function cycleIndex(i, n, d) return ((i - 1 + d) % n) + 1 end

local function delayText()
  local d = mp.get_property_number("sub-delay") or 0
  if math.abs(d) < 0.05 then return "0.0 s" end
  return string.format("%+.1f s", d) .. (d > 0 and tr(" (después)", " (later)") or tr(" (antes)", " (earlier)"))
end

local EDITOR = {
  { name = tr("Sincronía", "Sync"), value = delayText, change = function(d)
    local v = (mp.get_property_number("sub-delay") or 0) + d * 0.1
    mp.set_property_number("sub-delay", math.floor(v * 10 + 0.5) / 10)
  end },
  { name = tr("Tamaño", "Size"), value = function() return style.size .. " %" end,
    change = function(d) style.size = clampStep(style.size, d * 5, 50, 200); styleChanged() end },
  { name = tr("Posición", "Position"), value = function() return style.pos .. " %" end,
    change = function(d) style.pos = clampStep(style.pos, d, 0, 40); styleChanged() end },
  { name = tr("Letra", "Font"), value = function() return FONTS[style.font][1] end,
    change = function(d) style.font = cycleIndex(style.font, #FONTS, d); styleChanged() end },
  { name = tr("Color", "Color"), value = function() return COLORS[style.color][1] end,
    change = function(d) style.color = cycleIndex(style.color, #COLORS, d); styleChanged() end },
  { name = tr("Fondo", "Background"), value = function() return BACKS[style.back][1] end,
    change = function(d) style.back = cycleIndex(style.back, #BACKS, d); styleChanged() end },
  { name = tr("Borde", "Edge"), value = function() return EDGES[style.edge] end,
    change = function(d) style.edge = cycleIndex(style.edge, #EDGES, d); styleChanged() end },
  { name = tr("Estilo de los .ass", "ASS styling"), value = function() return style.original and tr("Original", "Original") or tr("El de Bambi", "Bambi's") end,
    change = function() style.original = not style.original; styleChanged() end },
  { name = tr("Restablecer", "Reset"), value = function() return "" end, change = function()
    style = copyStyle(DEFAULT_STYLE)
    mp.set_property_number("sub-delay", 0)
    styleChanged()
    say(tr("Subtítulos como al principio", "Subtitles reset"))
  end },
}

local editor, editorRow, editorPaused, sampleSid, editorPrevSid = false, 1, false, nil, nil

local function samplePath()
  local dir = os.getenv("TEMP") or os.getenv("TMPDIR") or "/tmp"
  return dir .. "/bambi-muestra-subs.srt"
end

-- Mientras se edita, el vídeo espera en pausa y se ve una frase de muestra con el estilo elegido.
local function openEditor()
  if editor then return end
  editor, editorRow, panel = true, 1, false
  editorPaused = mp.get_property_native("pause")
  mp.set_property_bool("pause", true)
  editorPrevSid = mp.get_property("sid") or "no"
  local f = io.open(samplePath(), "w")
  if f then
    f:write("1\n00:00:00,000 --> 99:59:59,000\n" .. tr("Así se verán los subtítulos", "This is how subtitles will look") .. "\n")
    f:close()
    mp.commandv("sub-add", samplePath(), "select", tr("Muestra", "Sample"))
    sampleSid = mp.get_property_number("sid")
  end
end

local function closeEditor()
  if not editor then return end
  editor = false
  if sampleSid then mp.commandv("sub-remove", tostring(sampleSid)); sampleSid = nil end
  mp.set_property("sid", editorPrevSid or "no")
  mp.set_property_bool("pause", editorPaused == true)
end

local function editorKey(key)
  if key == "UP" then editorRow = cycleIndex(editorRow, #EDITOR, -1)
  elseif key == "DOWN" then editorRow = cycleIndex(editorRow, #EDITOR, 1)
  elseif key == "LEFT" then EDITOR[editorRow].change(-1)
  elseif key == "RIGHT" or key == "ENTER" then EDITOR[editorRow].change(1)
  elseif key == "ESC" then closeEditor() end
end

local function drawEditor(out, s)
  -- Arriba a la derecha: la frase de muestra (abajo, en el centro) se ve entera mientras se edita.
  local w, rowH, pad = 600 * s, 44 * s, 26 * s
  local h = pad * 2 + 80 * s + #EDITOR * rowH + 66 * s
  local x, y = W - 48 * s - w, 120 * s
  addButton(0, 0, W, H, closeEditor)
  out[#out + 1] = rrect(x, y, w, h, 14 * s, "181414", 0x08)
  addButton(x, y, x + w, y + h, function() end)
  out[#out + 1] = text(x + pad, y + pad + 16 * s, 4, 28 * s, tr("Editar subtítulos", "Edit subtitles"), WHITE, 0, true)
  out[#out + 1] = text(x + pad, y + pad + 50 * s, 4, 17 * s,
    tr("‹ › o las flechas cambian el valor. El vídeo espera en pausa.", "‹ › or the arrow keys change the value. The video waits paused."), WHITE, 0x60, false)
  for i, row in ipairs(EDITOR) do
    local ry = y + pad + 80 * s + (i - 1) * rowH
    local selected = i == editorRow
    local hot = inside(x + 12 * s, ry, x + w - 12 * s, ry + rowH)
    if selected or hot then
      out[#out + 1] = rrect(x + 12 * s, ry + 3 * s, w - 24 * s, rowH - 6 * s, 8 * s, WHITE, selected and 0xDC or 0xEE)
    end
    local cy = ry + rowH / 2
    out[#out + 1] = text(x + pad, cy, 4, 21 * s, row.name, WHITE, 0, selected)
    local value = row.value()
    addButton(x + 12 * s, ry, x + w - 12 * s, ry + rowH, function()
      editorRow = i
      if value == "" then row.change(1) end
    end)
    if value ~= "" then
      local bx2 = x + w - pad
      local bx1 = bx2 - 250 * s
      out[#out + 1] = text(bx1, cy, 4, 26 * s, "‹", WHITE, selected and 0 or 0x50, true)
      out[#out + 1] = text(bx2, cy, 6, 26 * s, "›", WHITE, selected and 0 or 0x50, true)
      out[#out + 1] = text((bx1 + bx2) / 2, cy, 5, 20 * s, esc(value), WHITE, 0, selected)
      addButton(bx1 - 10 * s, ry, (bx1 + bx2) / 2, ry + rowH, function() editorRow = i; row.change(-1) end)
      addButton((bx1 + bx2) / 2, ry, bx2 + 10 * s, ry + rowH, function() editorRow = i; row.change(1) end)
    end
  end
  local bw, bh = 150 * s, 48 * s
  pill(out, x + w - pad - bw, y + h - pad - bh, bw, bh, tr("Listo", "Done"), true, closeEditor)
end

-- Subtítulos de internet: los busca Bambi (OpenSubtitles, Podnapisi) y los deja en user-data/bambi/subs.
local function internetItems()
  if not bambiListening() then return nil end
  local state = mp.get_property_native("user-data/bambi/subs-state") or ""
  local found = mp.get_property_native("user-data/bambi/subs") or {}
  local items = {}
  if state == "searching" then
    items[1] = { label = tr("Buscando…", "Searching…"), selected = false, fn = function() end }
    return items
  end
  for i, f in ipairs(found) do
    items[#items + 1] = { label = f.label or "?", selected = false, fn = function()
      request("sub" .. i)
      say(tr("Descargando subtítulo…", "Downloading subtitle…"))
    end }
  end
  if #found == 0 then
    local label = (state == "none") and tr("Nada · buscar otra vez", "None · search again") or tr("Buscar en internet", "Search online")
    items[1] = { label = label, selected = false, fn = function() request("subs") end }
  end
  return items
end

local function drawPanel(out, s, bottom)
  local audios, subs = tracks("audio"), tracks("sub")
  local net = internetItems()
  local rowsA, rowsS = math.min(#audios, 9), math.min(#subs + 2, 10)
  local rows = math.max(rowsA, rowsS, net and math.min(#net, 9) or 0, 1)
  local colW, rowH, pad = 330 * s, 44 * s, 28 * s
  local cols = net and 3 or 2
  local pw, ph = colW * cols + pad * (cols + 1), pad * 2 + 54 * s + rows * rowH
  local px, py = W - 48 * s - pw, bottom - ph
  out[#out + 1] = rrect(px, py, pw, ph, 12 * s, "181414", 0x08)
  addButton(px, py, px + pw, py + ph, function() end)
  local function column(x, title, items)
    out[#out + 1] = text(x, py + pad + 18 * s, 4, 26 * s, title, WHITE, 0, true)
    for i, it in ipairs(items) do
      if i > 10 then break end
      local y = py + pad + 54 * s + (i - 1) * rowH
      local hot = inside(x - 8 * s, y, x + colW, y + rowH)
      if it.selected then
        out[#out + 1] = text(x, y + rowH / 2, 4, 20 * s, ICON.check, WHITE, 0, false, ICONFONT)
      end
      out[#out + 1] = text(x + 34 * s, y + rowH / 2, 4, 21 * s, esc(cut(it.label, 34)), WHITE,
        (it.selected or hot) and 0x00 or 0x60, it.selected)
      addButton(x - 8 * s, y, x + colW, y + rowH, it.fn)
    end
  end
  local aItems = {}
  for _, t in ipairs(audios) do
    aItems[#aItems + 1] = { label = trackLabel(t), selected = t.selected, fn = function() mp.set_property_number("aid", t.id) end }
  end
  if #aItems == 0 then aItems[1] = { label = tr("Sin audio", "No audio"), selected = true, fn = function() end } end
  local anySub = false
  for _, t in ipairs(subs) do if t.selected then anySub = true end end
  local sItems = { { label = tr("Desactivados", "Off"), selected = not anySub, fn = function() mp.set_property("sid", "no") end } }
  for _, t in ipairs(subs) do
    sItems[#sItems + 1] = { label = trackLabel(t), selected = t.selected, fn = function() mp.set_property_number("sid", t.id) end }
  end
  sItems[#sItems + 1] = { label = tr("✎  Editar subtítulos…", "✎  Edit subtitles…"), selected = false, fn = openEditor }
  column(px + pad, "Audio", aItems)
  column(px + pad * 2 + colW, tr("Subtítulos", "Subtitles"), sItems)
  if net then column(px + pad * 3 + colW * 2, tr("En internet", "Online"), net) end
end

mp.observe_property("user-data/bambi/subs-state", "native", function(_, v)
  if type(v) ~= "string" then return end
  if v:sub(1, 6) == "added:" then say(tr("Subtítulos: ", "Subtitles: ") .. v:sub(7))
  elseif v == "none" then say(tr("No se han encontrado subtítulos", "No subtitles found"))
  elseif v == "error" then say(tr("No se pudieron buscar subtítulos", "Could not search subtitles")) end
end)

local function render()
  buttons = {}
  if W <= 0 or H <= 0 then return end
  local s = scale()
  local out = {}
  local pos = mp.get_property_number("time-pos")
  local dur = mp.get_property_number("duration")
  local paused = mp.get_property_native("pause")
  local barY = H - 122 * s
  local rowY = H - 60 * s

  if visible then
    gradient(out, 0, 170 * s, 0x40, 0xFF)
    gradient(out, H - 300 * s, 300 * s, 0xFF, 0x28)

    -- Volver.
    iconButton(out, 70 * s, 70 * s, 36 * s, ICON.back, goBack)

    -- Barra de tiempo y lo que queda.
    local x1, x2 = 48 * s, W - 48 * s - 130 * s
    bar = { x1 = x1, x2 = x2, y = barY }
    local hotBar = inside(x1 - 6 * s, barY - 20 * s, x2 + 6 * s, barY + 20 * s)
    local th = (hotBar or drag) and 8 * s or 5 * s
    out[#out + 1] = rect(x1, barY - th / 2, x2 - x1, th, WHITE, 0xB8)
    if dur and dur > 0 then
      local cache = mp.get_property_number("demuxer-cache-time")
      if cache and pos and cache > pos then
        local a, b = math.min(1, pos / dur), math.min(1, cache / dur)
        out[#out + 1] = rect(x1 + (x2 - x1) * a, barY - th / 2, (x2 - x1) * (b - a), th, WHITE, 0x90)
      end
      local frac = drag or math.min(1, math.max(0, (pos or 0) / dur))
      out[#out + 1] = rect(x1, barY - th / 2, (x2 - x1) * frac, th, ACCENT, 0)
      out[#out + 1] = circle(x1 + (x2 - x1) * frac, barY, (hotBar or drag) and 11 * s or 8 * s, ACCENT, 0)
      out[#out + 1] = text(W - 48 * s, barY, 6, 24 * s, fmt(dur - frac * dur), WHITE, 0, false)
      if hotBar or drag then
        local hx = drag and (x1 + (x2 - x1) * drag) or math.max(x1, math.min(x2, mouse.x))
        local t = (hx - x1) / (x2 - x1) * dur
        local label = fmt(t)
        local tw = (#label * 13 + 28) * s
        out[#out + 1] = rrect(hx - tw / 2, barY - 62 * s, tw, 38 * s, 6 * s, "181414", 0x10)
        out[#out + 1] = text(hx, barY - 43 * s, 5, 22 * s, label, WHITE, 0, true)
      end
      addButton(x1 - 6 * s, barY - 20 * s, x2 + 6 * s, barY + 20 * s, nil, "seek")
    end

    -- Fila de abajo: reproducir, -10, +10, volumen.
    local size = 40 * s
    iconButton(out, 72 * s, rowY, size, paused and ICON.play or ICON.pause, togglePause)
    iconButton(out, 158 * s, rowY, size, ICON.rew, function() seekBy(-10) end, "10")
    iconButton(out, 244 * s, rowY, size, ICON.fwd, function() seekBy(10) end, "10")
    local muted = mp.get_property_native("mute")
    local volume = mp.get_property_number("volume") or 100
    local vx = 330 * s
    local zone = { vx - size, rowY - size, vx + 190 * s, rowY + size }
    volumeOpen = volumeDrag or inside(zone[1], zone[2], volumeOpen and zone[3] or vx + size, zone[4])
    iconButton(out, vx, rowY, size, (muted or volume == 0) and ICON.mute or ICON.vol, function() mp.command("cycle mute"); show() end)
    if volumeOpen then
      local sx1, sx2 = vx + 40 * s, vx + 170 * s
      vol = { x1 = sx1, x2 = sx2 }
      local f = muted and 0 or volume / 100
      out[#out + 1] = rect(sx1, rowY - 2 * s, sx2 - sx1, 4 * s, WHITE, 0xA0)
      out[#out + 1] = rect(sx1, rowY - 2 * s, (sx2 - sx1) * f, 4 * s, WHITE, 0)
      out[#out + 1] = circle(sx1 + (sx2 - sx1) * f, rowY, 8 * s, WHITE, 0)
      addButton(sx1 - 10 * s, rowY - 20 * s, sx2 + 10 * s, rowY + 20 * s, nil, "volume")
    end

    -- Título en el centro.
    local title = mp.get_property("media-title") or ""
    local room = math.max(10, math.floor((W - 1100 * s) / (12 * s)))
    out[#out + 1] = text(W / 2, rowY, 5, 24 * s, esc(cut(title, room)), WHITE, 0, true)

    -- Derecha: siguiente episodio, audio y subtítulos, pantalla completa.
    local rx = W - 72 * s
    if bambiListening() then
      iconButton(out, rx, rowY, size, isFullscreen() and ICON.window or ICON.full, function() request("fs") end)
      rx = rx - 90 * s
    end
    iconButton(out, rx, rowY, size, ICON.cc, function() panel = not panel; show() end)
    rx = rx - 90 * s
    if o.next == "yes" then
      iconButton(out, rx, rowY, size, ICON.nextep, goNext)
    end

    if panel then drawPanel(out, s, barY - 40 * s) end
  end

  -- Omitir intro / resumen (siempre, como en Netflix).
  local liftY = visible and (barY - 50 * s) or (H - 70 * s)
  if current and not panel then
    local label = current.name == "intro" and tr("Omitir intro", "Skip intro") or tr("Omitir resumen", "Skip recap")
    local bw, bh = 240 * s, 60 * s
    pill(out, W - 48 * s - bw, liftY - bh, bw, bh, label, false, skip)
  elseif counting and not dismissed and nextAt and not panel then
    local left = math.max(0, math.ceil(nextAt - (pos or 0)))
    local bw, bh = 330 * s, 60 * s
    local x = W - 48 * s - bw
    pill(out, x, liftY - bh, bw, bh, tr("Siguiente episodio · ", "Next episode · ") .. left, true, goNext)
    local cw = 220 * s
    pill(out, x - 16 * s - cw, liftY - bh, cw, bh, tr("Ver créditos", "Watch credits"), false, function() dismissed = true end)
  end

  if editor then drawEditor(out, s) end

  -- Cargando.
  if mp.get_property_native("paused-for-cache") or (mp.get_property_native("seeking") and not paused) then
    local pct = mp.get_property_number("cache-buffering-state")
    local label = tr("Cargando", "Loading") .. ((pct and pct > 0 and pct < 100) and ("… " .. math.floor(pct) .. " %") or "…")
    out[#out + 1] = rrect(W / 2 - 130 * s, H / 2 - 30 * s, 260 * s, 60 * s, 30 * s, BLACK, 0x50)
    out[#out + 1] = text(W / 2, H / 2, 5, 24 * s, label, WHITE, 0, true)
  end

  if toast and now() < toastUntil then
    out[#out + 1] = rrect(W / 2 - 260 * s, 46 * s, 520 * s, 56 * s, 28 * s, BLACK, 0x40)
    out[#out + 1] = text(W / 2, 74 * s, 5, 22 * s, esc(toast), WHITE, 0, true)
  end

  local data = table.concat(out, "\n")
  if data ~= lastData or W ~= lastW or H ~= lastH then
    ov.res_x, ov.res_y = W, H
    ov.data = data
    ov:update()
    lastData, lastW, lastH = data, W, H
  end
end

-- Ratón --------------------------------------------------------------------------------------------------------------
local function barFraction()
  return math.max(0, math.min(1, (mouse.x - bar.x1) / (bar.x2 - bar.x1)))
end

local function setVolumeFromMouse()
  local f = math.max(0, math.min(1, (mouse.x - vol.x1) / (vol.x2 - vol.x1)))
  mp.set_property_number("volume", math.floor(f * 100 + 0.5))
  mp.set_property_bool("mute", false)
end

local function onDown()
  show()
  local b = hit()
  if b and b.kind == "seek" then drag = barFraction()
  elseif b and b.kind == "volume" then volumeDrag = true; setVolumeFromMouse() end
  render()
end

local function onUp()
  if drag then
    local d = mp.get_property_number("duration")
    if d then mp.commandv("seek", tostring(drag * d), "absolute") end
    drag = nil
    render()
    return
  end
  if volumeDrag then volumeDrag = false; return end
  local b = hit()
  if b then
    if b.fn then b.fn() end
    render()
    return
  end
  if panel then panel = false; render(); return end
  -- Clic en el vídeo: pausa; doble clic: pantalla completa (se espera un poco por si llega el segundo clic).
  if pendingClick then
    pendingClick:kill()
    pendingClick = nil
    if bambiListening() then request("fs") end
    return
  end
  pendingClick = mp.add_timeout(0.3, function() pendingClick = nil; togglePause() end)
end

local isDown = false
mp.add_forced_key_binding("MBTN_LEFT", "bambi-mouse", function(e)
  if e.event == "down" then
    isDown = true
    onDown()
  elseif e.event == "up" then
    if isDown then isDown = false; onUp() end
  elseif e.event == "press" and not isDown then
    onDown(); onUp()
  end
end, { complex = true })

-- El doble clic lo cuenta el propio script (arriba); el de mpv no hace nada.
mp.add_forced_key_binding("MBTN_LEFT_DBL", "bambi-dbl", function() end)
mp.register_script_message("bambi-mouse-leave", function() mouse.inside = false end)

mp.add_forced_key_binding("WHEEL_UP", "bambi-wheel-up", function() changeVolume(5) end)
mp.add_forced_key_binding("WHEEL_DOWN", "bambi-wheel-down", function() changeVolume(-5) end)

mp.observe_property("mouse-pos", "native", function(_, p)
  if not p then return end
  local moved = p.x ~= mouse.x or p.y ~= mouse.y
  mouse.x, mouse.y, mouse.inside = p.x, p.y, true
  if drag then drag = barFraction() end
  if volumeDrag then setVolumeFromMouse() end
  if moved and mouse.inside then show() end
end)

mp.observe_property("osd-dimensions", "native", function(_, d)
  if d then W, H = d.w or 0, d.h or 0 end
end)

-- Teclado (como Netflix en el PC) -----------------------------------------------------------------------------------
mp.add_forced_key_binding("SPACE", "bambi-space", function() if not editor then togglePause() end end)
mp.add_forced_key_binding("ENTER", "bambi-enter", function() if editor then editorKey("ENTER") else togglePause() end end)
mp.add_forced_key_binding("KP_ENTER", "bambi-kpenter", function() if editor then editorKey("ENTER") else togglePause() end end)
mp.add_forced_key_binding("LEFT", "bambi-left", function() if editor then editorKey("LEFT") else seekBy(-10) end end, { repeatable = true })
mp.add_forced_key_binding("RIGHT", "bambi-right", function() if editor then editorKey("RIGHT") else seekBy(10) end end, { repeatable = true })
mp.add_forced_key_binding("UP", "bambi-up", function() if editor then editorKey("UP") else changeVolume(5) end end, { repeatable = true })
mp.add_forced_key_binding("DOWN", "bambi-down", function() if editor then editorKey("DOWN") else changeVolume(-5) end end, { repeatable = true })
mp.add_forced_key_binding("e", "bambi-edit-subs", function()
  if editor then closeEditor() else openEditor() end
  if bambiListening() then lastUi = (panel or editor) and 1 or 0; request("ui" .. lastUi) end
end)
mp.add_forced_key_binding("m", "bambi-mute", function() mp.command("cycle mute"); show() end)
mp.add_forced_key_binding("c", "bambi-tracks", function()
  panel = not panel
  show()
  if bambiListening() then lastUi = (panel or editor) and 1 or 0; request("ui" .. lastUi) end
end)
mp.add_forced_key_binding("f", "bambi-fs", function() if bambiListening() then request("fs") end end)
mp.add_forced_key_binding("F11", "bambi-f11", function() if bambiListening() then request("fs") end end)
mp.add_forced_key_binding("ESC", "bambi-esc", function()
  if editor then closeEditor(); return end
  if panel then panel = false; render(); return end
  if bambiListening() then request("esc") else goBack() end
end)
mp.add_forced_key_binding("s", "bambi-skip", skip)

-- Temporizador: T cambia entre apagado, 15, 30, 45, 60, 90 minutos y «al terminar el episodio».
local steps = { 0, 15, 30, 45, 60, 90, -1 }
local step, timer = 1, nil
local function label(v)
  if v == 0 then return tr("desactivado", "off") elseif v == -1 then return tr("al terminar el episodio", "at the end of the episode") else return v .. " min" end
end
mp.add_forced_key_binding("t", "bambi-sleep", function()
  step = step % #steps + 1
  local v = steps[step]
  if timer then timer:kill(); timer = nil end
  atEnd = (v == -1)
  if v > 0 then
    timer = mp.add_timeout(v * 60, function()
      mp.set_property_bool("pause", true)
      print("bambi-sleep-now")
      mp.command("quit")
    end)
  end
  say(tr("Temporizador de apagado: ", "Sleep timer: ") .. label(v))
end)

mp.add_forced_key_binding("n", "bambi-next", function() if o.next == "yes" and not atEnd then goNext() end end)

-- Intro, resumen y créditos ------------------------------------------------------------------------------------------
mp.observe_property("time-pos", "number", function(_, t)
  if not t then return end
  current = nil
  for _, rg in ipairs(skips) do
    local e = rg.e
    if e < 0 then e = math.huge end
    if t >= rg.s and t < e - 1 then current = rg end
  end
  if outro and o.next == "yes" and not atEnd and t >= outro[1] then
    if not counting then counting = true; nextAt = t + 10 end
    if t >= nextAt and not dismissed then goNext() end
  elseif counting and outro and t < outro[1] then
    counting, nextAt, dismissed = false, nil, false
  end
end)

mp.observe_property("pause", "bool", function(_, p) if p then show() end end)

mp.add_periodic_timer(0.1, function()
  if visible and now() > hideAt then
    local paused = mp.get_property_native("pause")
    local s = scale()
    local overControls = mouse.inside and (mouse.y > H - 220 * s or mouse.y < 130 * s)
    if not paused and not panel and not drag and not volumeDrag and not overControls then
      visible = false
      volumeOpen = false
    end
  end
  -- Con la barra a la vista, los subtítulos suben para no quedar debajo de los controles (como en Netflix).
  local margin = visible and (baseMargin + 130) or baseMargin
  if margin ~= subMargin then
    subMargin = margin
    mp.set_property_number("sub-margin-y", margin)
  end
  -- Bambi necesita saber si hay un panel abierto: entonces Escape lo cierra; si no, Escape sale del vídeo.
  local ui = (panel or editor) and 1 or 0
  if ui ~= lastUi and bambiListening() then
    lastUi = ui
    request("ui" .. ui)
  end
  render()
end)

local function isSpanish(lang)
  lang = (lang or ""):lower()
  return lang == "spa" or lang == "es" or lang == "esp" or lang == "spl"
end

mp.register_event("file-loaded", function()
  show(3)
  applyStyle()
  -- Audio que no es español y sin subtítulos en español: Bambi los busca solo (si está activado en Ajustes).
  if o.autosubs == "yes" then
    mp.add_timeout(2, function()
      if isSpanish(mp.get_property("current-tracks/audio/lang")) then return end
      for _, t in ipairs(tracks("sub")) do if isSpanish(t.lang) then return end end
      if bambiListening() then request("subsauto") end
    end)
  end
  if o.slate ~= "yes" then return end
  local d = mp.get_property_number("duration")
  if d and d > 0 and d < 240 then
    print("bambi-slate-now")
    mp.command("quit")
  end
end)

mp.register_event("end-file", function()
  if atEnd then print("bambi-sleep-episode") end
end)

mp.add_hook("on_unload", 50, function()
  local alang = mp.get_property("current-tracks/audio/lang") or ""
  local sid = mp.get_property("sid") or "no"
  local slang = "no"
  if sid ~= "no" then slang = mp.get_property("current-tracks/sub/lang") or "" end
  print("bambi-alang=" .. alang)
  print("bambi-slang=" .. slang)
end)
""".trimIndent()
}
