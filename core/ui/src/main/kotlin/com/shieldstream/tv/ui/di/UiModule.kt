package com.shieldstream.tv.ui.di

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.shieldstream.tv.model.DeviceProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val model = Build.MODEL
        return ImageLoader.Builder(context)
            // Fading every poster/backdrop adds animation and compositing work on low-power TV boxes.
            .crossfade(false)
            .memoryCache(
                MemoryCache.Builder(context)
                    .maxSizeBytes(DeviceProfile.imageMemoryCacheBytes(model))
                    .build(),
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DeviceProfile.imageDiskCacheBytes(model))
                    .build(),
            )
            .build()
    }
}
