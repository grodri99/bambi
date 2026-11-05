package com.shieldstream.tv.domain

import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.DeviceOptimization
import kotlinx.coroutines.flow.Flow

/**
 * Persistencia segura de la configuración de la app (token RD, proveedor de catálogo).
 */
interface SettingsRepository {
    val token: Flow<String?>
    suspend fun setToken(token: String?)

    val catalogProvider: Flow<CatalogProvider>
    suspend fun setCatalogProvider(provider: CatalogProvider)

    /** Optimización del dispositivo. En AUTOMATIC no se modifican los valores personalizados guardados. */
    val deviceOptimization: Flow<DeviceOptimization>
    suspend fun setDeviceOptimization(optimization: DeviceOptimization)

    /** Máximo buffer personalizado en segundos (20..100); AUTOMATIC usa la política detectada. */
    val maxBufferSeconds: Flow<Int>
    suspend fun setMaxBufferSeconds(seconds: Int)

    /** Interfaz ligera: fondos más pequeños y menos animaciones para TV boxes de baja potencia. */
    val lightweightUi: Flow<Boolean>
    val lightweightUiEnabled: Boolean
    suspend fun setLightweightUi(enabled: Boolean)

    /** Preferencia personalizada para ◀▶; AUTOMATIC decide según la memoria disponible. */
    val seekThumbnails: Flow<Boolean>
    suspend fun setSeekThumbnails(enabled: Boolean)

    /** Idioma de la app. Es fijo mientras la app está abierta: tras [setLanguage] hay que reiniciarla. */
    val language: AppLanguage
    suspend fun setLanguage(language: AppLanguage)

    /** Perfil activo: priorizar español (siempre activo cuando se exige solo español). */
    val spanishFirst: Flow<Boolean>
    suspend fun setSpanishFirst(enabled: Boolean)

    /** Perfil activo: solo fuentes en español, tanto en el panel como en la reproducción automática. */
    val spanishOnlySources: Flow<Boolean>
    suspend fun setSpanishOnlySources(enabled: Boolean)

    /** Series: tarjeta "Siguiente episodio" en los créditos y paso automático al terminar (por defecto, sí). */
    val autoNextEpisode: Flow<Boolean>
    suspend fun setAutoNextEpisode(enabled: Boolean)

    /** Teclado de Buscar: true = QWERTY; false = alfabético (ABC…), el de siempre. */
    val searchQwerty: Flow<Boolean>
    suspend fun setSearchQwerty(enabled: Boolean)

    /** Perfil activo: botones de categoría OCULTOS (por nombre de Category). Vacío = se ven todos. */
    val hiddenCategories: Flow<Set<String>>
    suspend fun setHiddenCategories(names: Set<String>)

    /** Buscadores de fuentes activados (por defecto, todos). */
    val sourceProviders: Flow<Set<SourceProvider>>
    suspend fun setSourceProviders(providers: Set<SourceProvider>)

    /** Servicio debrid en uso (Real-Debrid por defecto). */
    val debridService: Flow<DebridService>
    suspend fun setDebridService(service: DebridService)
}
