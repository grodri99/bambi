package com.shieldstream.tv.network

import com.shieldstream.tv.model.Lang
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiConfig {
    const val ALLDEBRID_BASE_URL = "https://api.alldebrid.com/"
    const val REAL_DEBRID_BASE_URL = "https://api.real-debrid.com/"
    const val TORRENTIO_BASE_URL = "https://torrentio.strem.fun/"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/"
}

/** Cómo se crean los clientes de las APIs, igual en Android (Hilt) y en escritorio. */
object ApiFactory {
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /** TMDB responde en el idioma de la app ([Lang]). */
    fun tmdbClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.newBuilder().addQueryParameter("language", Lang.current.tmdb).build()
            chain.proceed(request.newBuilder().url(url).build())
        }
        .build()
}
