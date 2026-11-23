package com.shieldstream.tv.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shieldstream.tv.model.ProfileTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Colores de Bambi: los mismos tokens que la app de la tele (core/ui AppColors). */
object BambiColors {
    // Negro cálido y superficies con un punto de profundidad, como las apps de cine de escritorio.
    val Background = Color(0xFF09090B)
    val BackgroundRaised = Color(0xFF111115)
    val Surface = Color(0xFF141418)
    val SurfaceHigh = Color(0xFF212128)
    val Placeholder = Color(0xFF1B1B21)
    /** Borde fino de tarjetas y paneles. */
    val Stroke = Color.White.copy(alpha = 0.07f)
    /** Fondo de los botones secundarios y paneles sobre imagen. */
    val Glass = Color.White.copy(alpha = 0.12f)
    val Gold = Color(0xFFF5C451)
    /** Acento del perfil activo (Bambi, Océano…): cambia al cambiar de perfil y todo lo rojo se repinta. */
    var theme by mutableStateOf(ProfileTheme.BAMBI)
    val Accent: Color get() = Color(theme.accent)
    val AccentDark: Color get() = Color(theme.accentDark)
    val OnAccent: Color get() = Color(theme.onAccent)
    val Text = Color.White
    val Muted = Color.White.copy(alpha = 0.62f)
    val Cached = Color(0xFF46D369)
}

/** Radios de las esquinas, como AppShapes en la tele. */
object BambiShapes {
    val Badge = RoundedCornerShape(5.dp)
    val Control = RoundedCornerShape(10.dp)
    val Panel = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(50)
    val Poster = RoundedCornerShape(12.dp)
}

@Composable
fun BambiTheme(content: @Composable () -> Unit) {
    val font = BambiFonts.ui
    MaterialTheme(
        typography = androidx.compose.material3.Typography().let { t ->
            t.copy(
                displayLarge = t.displayLarge.copy(fontFamily = font), headlineLarge = t.headlineLarge.copy(fontFamily = font),
                headlineMedium = t.headlineMedium.copy(fontFamily = font), titleLarge = t.titleLarge.copy(fontFamily = font),
                titleMedium = t.titleMedium.copy(fontFamily = font), bodyLarge = t.bodyLarge.copy(fontFamily = font),
                bodyMedium = t.bodyMedium.copy(fontFamily = font), bodySmall = t.bodySmall.copy(fontFamily = font),
                labelLarge = t.labelLarge.copy(fontFamily = font), labelMedium = t.labelMedium.copy(fontFamily = font),
                labelSmall = t.labelSmall.copy(fontFamily = font),
            )
        },
        colorScheme = darkColorScheme(
            primary = BambiColors.Accent,
            onPrimary = BambiColors.OnAccent,
            background = BambiColors.Background,
            surface = BambiColors.Surface,
            surfaceVariant = BambiColors.SurfaceHigh,
            onSurface = BambiColors.Text,
            onSurfaceVariant = BambiColors.Muted,
            secondaryContainer = Color.White.copy(alpha = 0.18f),
            onSecondaryContainer = Color.White,
        ),
        content = { androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily = font), content) },
    )
}

/** Letra del sistema: Segoe UI Variable en Windows 11 (la de las apps nativas); si no está, la normal. */
object BambiFonts {
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val ui: androidx.compose.ui.text.font.FontFamily by lazy {
        runCatching {
            androidx.compose.ui.text.font.FontFamily(
                listOf(
                    androidx.compose.ui.text.font.FontWeight.Normal, androidx.compose.ui.text.font.FontWeight.Medium,
                    androidx.compose.ui.text.font.FontWeight.SemiBold, androidx.compose.ui.text.font.FontWeight.Bold,
                    androidx.compose.ui.text.font.FontWeight.Black,
                ).map { androidx.compose.ui.text.platform.SystemFont("Segoe UI Variable Display", it) },
            )
        }.getOrDefault(androidx.compose.ui.text.font.FontFamily.Default)
    }
}

/** Caché de imágenes en memoria (las últimas 300): pósteres, fondos, logos e iconos de perfil. */
object ImageCache {
    private val client = OkHttpClient()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 300
    }

    suspend fun load(url: String): ImageBitmap? {
        if (url.startsWith(BundledImages.ANDROID_ASSET)) return BundledImages.get(url.removePrefix(BundledImages.ANDROID_ASSET))
        synchronized(cache) { cache[url] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (url.startsWith("file:")) {
                    java.io.File(java.net.URI(url)).readBytes()
                } else {
                    client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
                }?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
            }.getOrNull()
        }?.also { synchronized(cache) { cache[url] = it } }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: @Composable () -> Unit = {},
) {
    val bitmap by produceState<ImageBitmap?>(null, url) { value = url?.let { ImageCache.load(it) } }
    Box(modifier) {
        val b = bitmap
        if (b != null) Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale, alignment = alignment)
        else placeholder()
    }
}

/**
 * Logo de un título (TMDB). Algunos son oscuros (azul marino, negro) y sobre el fondo de Bambi casi no se ven: esos
 * se pintan en blanco, como hacen Netflix y compañía con los suyos.
 */
@Composable
fun LogoImage(
    url: String,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomStart,
    placeholder: @Composable () -> Unit = {},
) {
    val bitmap by produceState<ImageBitmap?>(null, url) { value = ImageCache.load(url) }
    Box(modifier) {
        val b = bitmap
        if (b == null) placeholder()
        else {
            val dark = remember(b) { LogoTone.isDark(b) }
            Image(
                b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = alignment,
                colorFilter = if (dark) androidx.compose.ui.graphics.ColorFilter.tint(Color.White) else null,
            )
        }
    }
}

object LogoTone {
    /**
     * Brillo medio (0–1, el canal más alto de cada píxel, como el «valor» de HSV) de los píxeles visibles del logo.
     * Así un logo rojo intenso cuenta como claro y uno azul marino como oscuro.
     */
    fun luminance(bitmap: ImageBitmap): Float {
        val pixels = bitmap.toPixelMap()
        val stepX = (pixels.width / 80).coerceAtLeast(1)
        val stepY = (pixels.height / 40).coerceAtLeast(1)
        var sum = 0.0
        var weight = 0.0
        for (y in 0 until pixels.height step stepY) for (x in 0 until pixels.width step stepX) {
            val c = pixels[x, y]
            if (c.alpha < 0.5f) continue
            sum += maxOf(c.red, c.green, c.blue) * c.alpha
            weight += c.alpha
        }
        return if (weight == 0.0) 1f else (sum / weight).toFloat()
    }

    fun isDark(bitmap: ImageBitmap): Boolean = runCatching { luminance(bitmap) < 0.6f }.getOrDefault(false)
}

/**
 * Póster como en la tele: al pasar el ratón (o con el foco) crece un poco y se marca con un borde blanco.
 * [progress] dibuja la barra roja de lo que llevas visto.
 */
@Composable
fun PosterCard(item: CatalogItem, onClick: () -> Unit, width: Dp = 158.dp, progress: Float? = null) {
    val interaction = remember { MutableInteractionSource() }
    // Al volver de una ficha, el foco vuelve al póster desde el que se abrió (como en la tele).
    val restore = LocalFocusRestore.current
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (restore != null && restore.key == item.key) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            restore.key = null
        }
    }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    // Con el ratón solo se marca lo que tiene el puntero encima (el foco de un clic no deja otro póster marcado).
    val active = hovered || (focused && InputMode.keyboard)
    val scale by animateFloatAsState(if (active) 1.06f else 1f, androidx.compose.animation.core.tween(220))
    val lift by animateFloatAsState(if (active) 22f else 0f, androidx.compose.animation.core.tween(220))
    Column(Modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                // Al pasar por encima se levanta con sombra y un filo claro, como un póster de verdad.
                .shadow(lift.dp, BambiShapes.Poster, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(BambiShapes.Poster)
                .background(BambiColors.Placeholder)
                .border(if (active) 2.dp else 1.dp, if (active) Color.White.copy(alpha = 0.9f) else BambiColors.Stroke, BambiShapes.Poster)
                .hoverable(interaction)
                .then(Modifier.focusRequester(requester))
                .clickable(interactionSource = interaction, indication = null) {
                    restore?.key = item.key
                    onClick()
                },
        ) {
            RemoteImage(
                url = item.posterUrl,
                modifier = Modifier.fillMaxSize(),
                placeholder = { TitlePlaceholder(item.title) },
            )
            if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
        Text(
            item.title,
            color = if (active) BambiColors.Text else BambiColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun TitlePlaceholder(title: String) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BambiColors.SurfaceHigh, BambiColors.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = BambiColors.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
    }
}

/** Barra de progreso roja sobre fondo translúcido (como en «Seguir viendo» de la tele). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(BambiShapes.Pill).background(Color.White.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(BambiColors.Accent))
    }
}

/** Resultado de una carga para las pantallas. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

suspend fun <T> load(block: suspend () -> T): Load<T> =
    runCatching { block() }.fold({ Load.Ok(it) }, { Load.Failed(it.message ?: "Error desconocido") })

/** Qué póster debe recuperar el foco al volver a una pantalla (el último que se abrió). */
class FocusRestore {
    var key: String? = null
}

val LocalFocusRestore = androidx.compose.runtime.staticCompositionLocalOf<FocusRestore?> { null }

/**
 * Bambi crece con la ventana, como una app de tele: el diseño está pensado para 1440 de ancho y en pantallas más
 * grandes (o a pantalla completa en 1440p/4K) todo se amplía en proporción (hasta 1,6×) en vez de quedarse pequeño.
 */
/** Densidad real de la pantalla (sin el escalado de [AdaptiveScale]), para lo que se coloca en píxeles de Windows. */
val LocalBaseDensity = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Density?> { null }

/** Si se está usando el teclado o el mando (se marca el elemento con foco) o el ratón (solo lo que hay debajo). */
object InputMode {
    var keyboard by androidx.compose.runtime.mutableStateOf(true)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AdaptiveScale(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) {
            if (InputMode.keyboard) InputMode.keyboard = false
        },
    ) {
        val base = androidx.compose.ui.platform.LocalDensity.current
        val factor = (maxWidth.value / 1440f).coerceIn(1f, 1.6f)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * factor, base.fontScale),
            LocalBaseDensity provides base,
        ) { content() }
    }
}
