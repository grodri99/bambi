package com.shieldstream.tv.domain

import com.shieldstream.tv.model.AppLanguage
import kotlinx.coroutines.flow.Flow

/**
 * Persistencia segura de la configuración de la app (token RD, proveedor de catálogo).
 */
interface SettingsRepository {
    val token: Flow<String?>
    suspend fun setToken(token: String?)

    val catalogProvider: Flow<CatalogProvider>
    suspend fun setCatalogProvider(provider: CatalogProvider)

    /** Máximo buffer del reproductor en segundos (20..100). */
    val maxBufferSeconds: Flow<Int>
    suspend fun setMaxBufferSeconds(seconds: Int)

    /** Interfaz ligera: fondos más pequeños y menos animaciones para TV boxes de baja potencia. */
    val lightweightUi: Flow<Boolean>
    val lightweightUiEnabled: Boolean
    suspend fun setLightweightUi(enabled: Boolean)

    /** ◀▶ en el reproductor: true = búsqueda con miniaturas; false = saltos directos de 10 s (como antes). */
    val seekThumbnails: Flow<Boolean>
    suspend fun setSeekThumbnails(enabled: Boolean)

    /** Idioma de la app. Es fijo mientras la app está abierta: tras [setLanguage] hay que reiniciarla. */
    val language: AppLanguage
    suspend fun setLanguage(language: AppLanguage)

    /** "Castellano primero": las fuentes con audio en español arriba. Por defecto, sí si la app está en español. */
    val spanishFirst: Flow<Boolean>
    suspend fun setSpanishFirst(enabled: Boolean)

    /** Panel de Fuentes: enseñar solo las que dicen traer audio en español (botón del propio panel). */
    val spanishOnlySources: Flow<Boolean>
    suspend fun setSpanishOnlySources(enabled: Boolean)

    /** Series: tarjeta "Siguiente episodio" en los créditos y paso automático al terminar (por defecto, sí). */
    val autoNextEpisode: Flow<Boolean>
    suspend fun setAutoNextEpisode(enabled: Boolean)

    /** Teclado de Buscar: true = QWERTY; false = alfabético (ABC…), el de siempre. */
    val searchQwerty: Flow<Boolean>
    suspend fun setSearchQwerty(enabled: Boolean)

    /** Botones de categoría del Inicio OCULTOS (por nombre de Category). Vacío = se ven todos. */
    val hiddenCategories: Flow<Set<String>>
    suspend fun setHiddenCategories(names: Set<String>)

    /** Buscadores de fuentes activados (por defecto, todos). */
    val sourceProviders: Flow<Set<SourceProvider>>
    suspend fun setSourceProviders(providers: Set<SourceProvider>)

    /** Servicio debrid en uso (Real-Debrid por defecto). */
    val debridService: Flow<DebridService>
    suspend fun setDebridService(service: DebridService)
}
