package com.shieldstream.tv.watchnext

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.shieldstream.tv.MainActivity
import com.shieldstream.tv.R
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.strings.R as StringsR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinueWatchingChannel @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val channelMutex = Mutex()

    @Volatile
    private var channelId: Long? = null

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun ensureChannel(): Long = channelMutex.withLock {
        channelId?.let { return@withLock it }

        val existing = readChannel()
        val id = if (existing == null) {
            val uri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channelValues(),
            )
            checkNotNull(uri) { "Android TV did not create the Continue watching channel" }
            ContentUris.parseId(uri)
        } else {
            context.contentResolver.update(
                TvContractCompat.buildChannelUri(existing.id),
                channelValues(),
                null,
                null,
            )
            existing.id
        }

        ChannelLogoUtils.storeChannelLogo(
            context,
            id,
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher),
        )
        channelId = id
        id
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun requestBrowsable() {
        if (preferences.getBoolean(KEY_VISIBILITY_REQUESTED, false)) return
        val id = ensureChannel()
        // Ask once per installation. If the user declines, they can still enable the channel
        // from Android TV's channel-customization screen without repeated prompts.
        preferences.edit().putBoolean(KEY_VISIBILITY_REQUESTED, true).apply()
        TvContractCompat.requestChannelBrowsable(context, id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal suspend fun sync(candidateIds: Set<String>, loaded: List<LauncherEntry>) {
        val id = ensureChannel()
        val existing = readPrograms(id)
        val retainedIds = mutableSetOf<String>()
        val retainedProviderIds = mutableSetOf<Long>()

        existing.forEach { program ->
            val internalId = program.internalId
            val retain = internalId != null && internalId in candidateIds && retainedIds.add(internalId)
            if (retain) {
                retainedProviderIds += program.providerId
            } else {
                // This also reconciles duplicate rows left by interrupted or older syncs.
                deleteProgram(program.providerId)
            }
        }

        val existingByInternalId = existing.mapNotNull { program ->
            program.internalId?.takeIf { program.providerId in retainedProviderIds }?.let { it to program }
        }.toMap()

        loaded.forEachIndexed { index, launcherEntry ->
            val values = programValues(id, index, launcherEntry)
            val current = existingByInternalId[launcherEntry.internalId]
            if (current == null) {
                context.contentResolver.insert(
                    TvContractCompat.buildPreviewProgramsUriForChannel(id),
                    values,
                )
            } else {
                context.contentResolver.update(
                    TvContractCompat.buildPreviewProgramUri(current.providerId),
                    values,
                    null,
                    null,
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun channelValues(): ContentValues = Channel.Builder()
        .setType(TvContractCompat.Channels.TYPE_PREVIEW)
        .setDisplayName(context.getString(StringsR.string.home_continue_watching))
        .setDescription(context.getString(StringsR.string.home_continue_watching))
        .setInternalProviderId(CHANNEL_INTERNAL_ID)
        .setAppLinkIntentUri(
            Uri.parse(
                Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME),
            ),
        )
        .build()
        .toContentValues()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun readChannel(): Channel? {
        context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val channel = Channel.fromCursor(cursor)
                if (channel.id >= 0L && channel.internalProviderId == CHANNEL_INTERNAL_ID) return channel
            }
        }
        return null
    }

    @SuppressLint("RestrictedApi") // Documented PreviewProgram cursor mapping inherits restricted getters.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun readPrograms(channelId: Long): List<ExistingPreviewProgram> {
        val programs = mutableListOf<ExistingPreviewProgram>()
        context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = PreviewProgram.fromCursor(cursor)
                if (program.id >= 0L) {
                    programs += ExistingPreviewProgram(program.id, program.internalProviderId)
                }
            }
        }
        return programs
    }

    private fun deleteProgram(providerId: Long) {
        context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramUri(providerId),
            null,
            null,
        )
    }

    private data class ExistingPreviewProgram(
        val providerId: Long,
        val internalId: String?,
    )

    companion object {
        private const val CHANNEL_INTERNAL_ID = "continue_watching"
        private const val PREFERENCES_NAME = "android_tv_channels"
        private const val KEY_VISIBILITY_REQUESTED = "continue_watching_visibility_requested"

        @SuppressLint("RestrictedApi") // Google's preview-program builder API triggers inherited-method lint errors.
        @RequiresApi(Build.VERSION_CODES.O)
        internal fun programValues(
            channelId: Long,
            weight: Int,
            launcherEntry: LauncherEntry,
        ): ContentValues {
            val item = launcherEntry.item
            val entry = launcherEntry.entry
            return PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(
                    if (item.type == MediaType.MOVIE) {
                        TvContractCompat.PreviewPrograms.TYPE_MOVIE
                    } else {
                        TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                    },
                )
                .setTitle(item.title)
                .setDescription(item.overview)
                .setIntentUri(launcherEntry.deepLink())
                .setInternalProviderId(launcherEntry.internalId)
                .setLastPlaybackPositionMillis(entry.progress.positionMs.asProviderMillis())
                .setDurationMillis(entry.progress.durationMs.asProviderMillis())
                .setWeight(weight)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                .setThumbnailAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .apply {
                    item.posterUrl?.takeIf { it.isNotBlank() }?.let { setPosterArtUri(Uri.parse(it)) }
                    item.backdropUrl?.takeIf { it.isNotBlank() }?.let { setThumbnailUri(Uri.parse(it)) }
                    entry.season?.let(::setSeasonNumber)
                    entry.episode?.let(::setEpisodeNumber)
                }
                .build()
                .toContentValues()
        }

        private fun Long.asProviderMillis(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
