package com.shieldstream.tv.network.di

import com.shieldstream.tv.network.ApiConfig
import com.shieldstream.tv.network.ApiFactory
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.RealDebridApi
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = ApiFactory.json()

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .dns(com.shieldstream.tv.network.CloudflareFallbackDns.also { dns -> dns.logger = { android.util.Log.w("CfDns", it) } })
        .eventListener(com.shieldstream.tv.network.CloudflareFallbackDns.failureListener)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable) addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                Log.d("Http", "${request.method} ${request.url.host} -> ${response.code}")
                response
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideAllDebridApi(client: OkHttpClient, json: Json): AllDebridApi =
        buildRetrofit(ApiConfig.ALLDEBRID_BASE_URL, client, json).create(AllDebridApi::class.java)

    @Provides
    @Singleton
    fun provideRealDebridApi(client: OkHttpClient, json: Json): RealDebridApi =
        buildRetrofit(ApiConfig.REAL_DEBRID_BASE_URL, client, json).create(RealDebridApi::class.java)

    @Provides
    @Singleton
    fun provideTorrentioApi(client: OkHttpClient, json: Json): TorrentioApi =
        buildRetrofit(ApiConfig.TORRENTIO_BASE_URL, client, json).create(TorrentioApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbApi(client: OkHttpClient, json: Json): TmdbApi {
        val tmdbClient = ApiFactory.tmdbClient(client)
        return buildRetrofit(ApiConfig.TMDB_BASE_URL, tmdbClient, json).create(TmdbApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        ApiFactory.retrofit(baseUrl, client, json)
}
