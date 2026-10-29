package com.shieldstream.tv.network.di

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.RealDebridApi
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

object ApiConfig {
    const val REAL_DEBRID_BASE_URL = "https://api.real-debrid.com/"
    const val ALL_DEBRID_BASE_URL = "https://api.alldebrid.com/"
    const val TORRENTIO_BASE_URL = "https://torrentio.strem.fun/"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/"
    // La clave de TMDB ya no va en el código: se guarda cifrada en el dispositivo y se cambia en Ajustes
    // (la primera vez se rellena con tmdbApiKey de local.properties, que no se sube a git).
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        // Conexión corta: con una IP bloqueada (ver CloudflareFallbackDns) se pasa enseguida a la siguiente.
        .connectTimeout(5, TimeUnit.SECONDS)
        .dns(com.shieldstream.tv.network.CloudflareFallbackDns)
        .eventListener(com.shieldstream.tv.network.CloudflareFallbackDns.failureListener)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            // Registro de peticiones solo en "Bambi (pruebas)": en la release no se imprimen URLs con claves.
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    @Provides
    @Singleton
    fun provideRealDebridApi(client: OkHttpClient, json: Json): RealDebridApi =
        buildRetrofit(ApiConfig.REAL_DEBRID_BASE_URL, client, json).create(RealDebridApi::class.java)

    @Provides
    @Singleton
    fun provideAllDebridApi(client: OkHttpClient, json: Json): AllDebridApi =
        buildRetrofit(ApiConfig.ALL_DEBRID_BASE_URL, client, json).create(AllDebridApi::class.java)

    @Provides
    @Singleton
    fun provideTorrentioApi(client: OkHttpClient, json: Json): TorrentioApi =
        buildRetrofit(ApiConfig.TORRENTIO_BASE_URL, client, json).create(TorrentioApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbApi(client: OkHttpClient, json: Json): TmdbApi {
        // Todo TMDB en el idioma de la app (títulos, sinopsis, géneros, episodios). Se lee en cada petición,
        // pero solo cambia al reiniciar la app (ver Lang).
        val tmdbClient = client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.newBuilder().addQueryParameter("language", Lang.current.tmdb).build()
                chain.proceed(request.newBuilder().url(url).build())
            }
            .build()
        return buildRetrofit(ApiConfig.TMDB_BASE_URL, tmdbClient, json).create(TmdbApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
