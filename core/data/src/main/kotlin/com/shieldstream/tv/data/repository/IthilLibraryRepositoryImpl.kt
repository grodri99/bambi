package com.shieldstream.tv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.SystemClock
import android.util.Log
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.shieldstream.tv.domain.IthilConfig
import com.shieldstream.tv.domain.IthilLibraryInfo
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IthilLibraryRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val config: IthilConfig,
    private val client: OkHttpClient,
) : IthilLibraryRepository {

    private val directory = File(context.filesDir, "ithil")
    private val indexFile = File(directory, "ithil-index.db")
    private val downloadFile = File(directory, "library.zm3")
    private val archiveFile = File(directory, "library.zip")
    private val sourceFile = File(directory, "source.db")
    private val buildingFile = File(directory, "index.build.db")
    private val decoder by lazy { IthilLinkDecoder(config) }
    private val mutationMutex = Mutex()

    override suspend fun install(
        downloadUrl: String,
        sourceSha: String,
        onProgress: (Float) -> Unit,
    ): IthilLibraryInfo = mutationMutex.withLock {
        check(config.isConfigured) { "Ithil is not configured" }
        require(downloadUrl.startsWith("https://")) { "Invalid Ithil library URL" }
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            deleteTemporaryFiles()
            val startedAt = SystemClock.elapsedRealtime()
            try {
                download(downloadUrl, onProgress)
                val downloadedAt = SystemClock.elapsedRealtime()
                extractSource()
                val extractedAt = SystemClock.elapsedRealtime()
                importExtractedSource(sourceSha)
                val importedAt = SystemClock.elapsedRealtime()
                Log.i(
                    "Ithil",
                    "Library install: download=${downloadedAt - startedAt}ms " +
                        "extract=${extractedAt - downloadedAt}ms import=${importedAt - extractedAt}ms " +
                        "total=${importedAt - startedAt}ms",
                )
            } finally {
                deleteTemporaryFiles()
            }
        }
        checkNotNull(info()) { "Ithil index is unavailable after import" }
    }

    override fun isReady(): Boolean = config.isConfigured && indexFile.isFile && indexFile.length() > 0L

    override fun info(): IthilLibraryInfo? {
        if (!isReady()) return null
        return runCatching {
            openIndex().use { db ->
                val metadata = mutableMapOf<String, String>()
                db.rawQuery("SELECT key,value FROM library_metadata", null).use { cursor ->
                    while (cursor.moveToNext()) metadata[cursor.getString(0)] = cursor.getString(1)
                }
                IthilLibraryInfo(
                    version = metadata["ithil_version"].orEmpty(),
                    sourceSha = metadata["source_sha"].orEmpty(),
                    movieLinks = count(db, "movie_sources"),
                    episodeLinks = count(db, "episode_sources"),
                )
            }
        }.getOrNull()
    }

    override fun movieSources(tmdb: Int): List<IthilSource> {
        if (!isReady()) return emptyList()
        return runCatching {
            openIndex().use { db ->
                db.rawQuery(
                    "SELECT url,quality,audio,info,updated FROM movie_sources WHERE tmdb=?",
                    arrayOf(tmdb.toString()),
                ).use { cursor -> cursor.toSources() }
            }
        }.getOrDefault(emptyList())
    }

    override fun episodeSources(tmdb: Int, season: Int, episode: Int): List<IthilSource> {
        if (!isReady()) return emptyList()
        return runCatching {
            openIndex().use { db ->
                db.rawQuery(
                    "SELECT url,quality,audio,info,updated FROM episode_sources WHERE tmdb=? AND season=? AND episode=?",
                    arrayOf(tmdb.toString(), season.toString(), episode.toString()),
                ).use { cursor -> cursor.toSources() }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun delete() {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                deleteTemporaryFiles()
                indexFile.delete()
            }
        }
    }

    private fun openIndex(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(indexFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun download(url: String, onProgress: (Float) -> Unit) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Ithil library download failed: HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Ithil library response is empty" }
            val total = body.contentLength()
            var read = 0L
            var lastProgress = -1
            body.byteStream().use { input ->
                downloadFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            val progress = ((read * 100) / total).toInt()
                            if (progress != lastProgress) {
                                onProgress((progress / 100f).coerceIn(0f, 1f))
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
            check(total < 0 || read == total) { "Ithil library download is incomplete" }
            onProgress(1f)
        }
    }

    @VisibleForTesting
    internal fun extractSourceForTest() = extractSource()

    private fun extractSource() {
        archiveFile.outputStream().use { output ->
            output.write(ZIP_PREFIX)
            downloadFile.inputStream().use { input -> input.copyTo(output) }
        }
        var found = false
        ZipFile(archiveFile).use { archive ->
            val entry = archive.getEntry("settings.xml")
            if (entry != null) {
                archive.getInputStream(entry).use { input ->
                    sourceFile.outputStream().use { output -> input.copyTo(output) }
                }
                found = true
            }
        }
        check(found && sourceFile.length() > 0) { "Ithil archive does not contain settings.xml" }
    }

    @VisibleForTesting
    internal suspend fun importSourceForTest(source: File, sourceSha: String = "fixture") = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            buildingFile.delete()
            try {
                importExtractedSource(sourceSha, source)
            } finally {
                buildingFile.delete()
            }
        }
    }

    private fun importExtractedSource(sourceSha: String, source: File = sourceFile) {
        buildingFile.delete()
        SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { input ->
            checkIntegrity(input)
            requireSchema(input)
            val version = input.rawQuery("SELECT version_addon FROM version LIMIT 1", null).use { cursor ->
                check(cursor.moveToFirst()) { "Ithil version metadata is missing" }
                cursor.getString(0)
            }
            SQLiteDatabase.openOrCreateDatabase(buildingFile, null).use { output ->
                createSchema(output)
                output.execSQL("INSERT INTO library_metadata VALUES (?,?)", arrayOf("ithil_version", version))
                output.execSQL("INSERT INTO library_metadata VALUES (?,?)", arrayOf("source_sha", sourceSha))
                copyMovies(input, output)
                copyEpisodes(input, output)
                output.execSQL("CREATE INDEX idx_movie_tmdb ON movie_sources(tmdb)")
                output.execSQL("CREATE INDEX idx_episode_lookup ON episode_sources(tmdb,season,episode)")
                output.execSQL("ANALYZE")
                checkIntegrity(output)
            }
        }
        check(buildingFile.renameTo(indexFile)) { "Unable to activate the Ithil index" }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA journal_mode=OFF", null).close()
        db.rawQuery("PRAGMA synchronous=OFF", null).close()
        db.rawQuery("PRAGMA temp_store=MEMORY", null).close()
        db.execSQL("CREATE TABLE movie_sources (tmdb INTEGER NOT NULL,url TEXT NOT NULL,quality TEXT,audio TEXT,info TEXT,updated TEXT)")
        db.execSQL("CREATE TABLE episode_sources (tmdb INTEGER NOT NULL,season INTEGER NOT NULL,episode INTEGER NOT NULL,url TEXT NOT NULL,quality TEXT,audio TEXT,info TEXT,updated TEXT)")
        db.execSQL("CREATE TABLE library_metadata (key TEXT PRIMARY KEY,value TEXT)")
    }

    private fun copyMovies(input: SQLiteDatabase, output: SQLiteDatabase) {
        val statement = output.compileStatement("INSERT INTO movie_sources VALUES (?,?,?,?,?,?)")
        input.rawQuery("SELECT tmdb,link,calidad,audio,info,updated FROM enlaces_pelis", null).use { cursor ->
            output.beginTransaction()
            try {
                while (cursor.moveToNext()) {
                    statement.clearBindings()
                    statement.bindLong(1, cursor.getLong(0))
                    statement.bindString(2, decoder.decrypt(cursor.getString(1)))
                    statement.bindNullableString(3, cursor.getString(2))
                    statement.bindNullableString(4, cursor.getString(3))
                    statement.bindNullableString(5, cursor.getString(4))
                    statement.bindNullableString(6, cursor.getString(5))
                    statement.executeInsert()
                }
                output.setTransactionSuccessful()
            } finally {
                output.endTransaction()
                statement.close()
            }
        }
    }

    private fun copyEpisodes(input: SQLiteDatabase, output: SQLiteDatabase) {
        val statement = output.compileStatement("INSERT INTO episode_sources VALUES (?,?,?,?,?,?,?,?)")
        input.rawQuery("SELECT tmdb,temporada,episodio,link,calidad,audio,info,updated FROM enlaces_series", null).use { cursor ->
            output.beginTransaction()
            try {
                while (cursor.moveToNext()) {
                    statement.clearBindings()
                    statement.bindLong(1, cursor.getLong(0))
                    statement.bindLong(2, cursor.getInt(1).toLong())
                    statement.bindLong(3, cursor.getInt(2).toLong())
                    statement.bindString(4, decoder.decrypt(cursor.getString(3)))
                    statement.bindNullableString(5, cursor.getString(4))
                    statement.bindNullableString(6, cursor.getString(5))
                    statement.bindNullableString(7, cursor.getString(6))
                    statement.bindNullableString(8, cursor.getString(7))
                    statement.executeInsert()
                }
                output.setTransactionSuccessful()
            } finally {
                output.endTransaction()
                statement.close()
            }
        }
    }

    private fun SQLiteStatement.bindNullableString(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindString(index, value)
    }

    private fun checkIntegrity(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Ithil database integrity check failed" }
        }
    }

    private fun requireSchema(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('enlaces_pelis','enlaces_series','version')",
            null,
        ).use { cursor -> check(cursor.count == 3) { "Ithil database schema is unsupported" } }
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun android.database.Cursor.toSources(): List<IthilSource> {
        val result = ArrayList<IthilSource>(count)
        while (moveToNext()) {
            result += IthilSource(
                url = getString(0),
                quality = getString(1).orEmpty(),
                audio = getString(2).orEmpty(),
                info = getString(3).orEmpty(),
                updated = getString(4).orEmpty(),
            )
        }
        return result
    }

    private fun deleteTemporaryFiles() {
        downloadFile.delete()
        archiveFile.delete()
        sourceFile.delete()
        buildingFile.delete()
    }

    private companion object {
        val ZIP_PREFIX: ByteArray = Base64.decode(
            "UEsDBBQAAAAIAKJchVngR+RlsfTRAQBwSwUMAAAAc2V0dGluZ3MueG1s",
            Base64.DEFAULT,
        )
    }
}
