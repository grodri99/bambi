package com.shieldstream.tv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.IthilConfig
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IthilLibraryRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var repository: IthilLibraryRepositoryImpl
    private lateinit var fixture: File
    private val blob = ByteArray(48) { it.toByte() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "ithil").deleteRecursively()
        val config = IthilConfig("https://metadata.test", Base64.encodeToString(blob, Base64.NO_WRAP), "fixture")
        repository = IthilLibraryRepositoryImpl(context, config, OkHttpClient())
        fixture = File(context.filesDir, "ithil-fixture.db")
        fixture.delete()
    }

    @After
    fun tearDown() {
        fixture.delete()
        File(context.filesDir, "ithil").deleteRecursively()
    }

    @Test
    fun emptyConfigurationDisablesRepositoryWithoutCreatingDecoder() {
        val unconfigured = IthilLibraryRepositoryImpl(
            context,
            IthilConfig(metadataUrl = "", linkSecret = "", archivePrefix = ""),
            OkHttpClient(),
        )

        assertFalse(unconfigured.isReady())
        assertNull(unconfigured.info())
        assertTrue(unconfigured.movieSources(424242).isEmpty())
        assertTrue(unconfigured.episodeSources(515151, 1, 1).isEmpty())
    }

    @Test
    fun importsPlainUrlsAndUsesExactTmdbIndexes() = runTest {
        createFixture()

        repository.importSourceForTest(fixture, "remote-sha")

        val index = File(context.filesDir, "ithil/ithil-index.db")
        assertTrue("index=$index exists=${index.exists()} files=${index.parentFile?.list()?.toList()}", repository.isReady())
        assertEquals(3, repository.movieSources(424242).size)
        assertEquals(2, repository.episodeSources(515151, 1, 1).size)
        assertEquals("https://hoster.test/?movie0", repository.movieSources(424242).first().url)
        assertTrue(repository.movieSources(0).isEmpty())
        assertTrue(repository.episodeSources(515151, 2, 2).isEmpty())
        val info = repository.info()!!
        assertEquals("remote-sha", info.sourceSha)
        assertEquals(1003, info.movieLinks)
        assertEquals(1003, info.episodeLinks)
        val plans = queryPlans()
        assertTrue(plans.toString(), plans.any { "idx_movie_tmdb" in it })
        assertTrue(plans.toString(), plans.any { "idx_episode_lookup" in it })
    }

    @Test
    fun extractionUsesCentralDirectoryWhenLocalEntrySizesAreStale() = runTest {
        createFixture()
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("settings.xml"))
            zip.write(fixture.readBytes())
            zip.closeEntry()
        }
        val prefix = Base64.decode(
            "UEsDBBQAAAAIAKJchVngR+RlsfTRAQBwSwUMAAAAc2V0dGluZ3MueG1s",
            Base64.DEFAULT,
        )
        val archive = File(context.filesDir, "ithil/library.zm3")
        archive.parentFile?.mkdirs()
        val zipBytes = bytes.toByteArray()
        archive.writeBytes(prefix + zipBytes.copyOfRange(prefix.size, zipBytes.size))

        repository.extractSourceForTest()
        repository.importSourceForTest(File(context.filesDir, "ithil/source.db"))

        assertEquals(3, repository.movieSources(424242).size)
        assertEquals(2, repository.episodeSources(515151, 1, 1).size)
    }

    @Test
    fun failedImportPreservesPreviousIndexAndDeleteRemovesIt() = runTest {
        createFixture()
        repository.importSourceForTest(fixture)
        fixture.writeText("not sqlite")

        assertNotNull(runCatching { repository.importSourceForTest(fixture) }.exceptionOrNull())

        assertEquals(3, repository.movieSources(424242).size)
        repository.delete()
        assertFalse(repository.isReady())
        assertTrue(repository.movieSources(424242).isEmpty())
    }

    private fun createFixture() {
        SQLiteDatabase.openOrCreateDatabase(fixture, null).use { db ->
            db.execSQL("CREATE TABLE version (version_addon TEXT)")
            db.execSQL("INSERT INTO version VALUES ('3.3.11')")
            db.execSQL("CREATE TABLE enlaces_pelis (tmdb INTEGER,link TEXT,calidad TEXT,audio TEXT,info TEXT,updated TEXT)")
            db.execSQL("CREATE TABLE enlaces_series (tmdb INTEGER,temporada INTEGER,episodio INTEGER,link TEXT,calidad TEXT,audio TEXT,info TEXT,updated TEXT)")
            repeat(3) { index ->
                db.execSQL(
                    "INSERT INTO enlaces_pelis VALUES (424242,?,?,?,?,?)",
                    arrayOf(encrypt("https://hoster.test/?movie$index"), "1080p", "esp", "AC3", "2026-01-01"),
                )
            }
            repeat(1000) { index ->
                db.execSQL(
                    "INSERT INTO enlaces_pelis VALUES (1,?,?,?,?,?)",
                    arrayOf(encrypt("https://hoster.test/?dummy$index"), "1080p", "esp", "AC3", "2026-01-01"),
                )
            }
            repeat(2) { index ->
                db.execSQL(
                    "INSERT INTO enlaces_series VALUES (515151,1,1,?,?,?,?,?)",
                    arrayOf(encrypt("https://hoster.test/?episode$index"), "1080p", "esp,eng", "AC3", "2026-01-01"),
                )
            }
            repeat(1000) { index ->
                db.execSQL(
                    "INSERT INTO enlaces_series VALUES (1,1,1,?,?,?,?,?)",
                    arrayOf(encrypt("https://hoster.test/?dummyEpisode$index"), "1080p", "esp", "AC3", "2026-01-01"),
                )
            }
            db.execSQL(
                "INSERT INTO enlaces_series VALUES (515151,1,2,?,?,?,?,?)",
                arrayOf(encrypt("https://hoster.test/?other"), "1080p", "esp", "AC3", "2026-01-01"),
            )
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/OFB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(blob, 16, 32, "AES"), IvParameterSpec(blob, 0, 16))
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun queryPlans(): List<String> {
        val file = File(context.filesDir, "ithil/ithil-index.db")
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val result = mutableListOf<String>()
            listOf(
                "EXPLAIN QUERY PLAN SELECT * FROM movie_sources WHERE tmdb=424242",
                "EXPLAIN QUERY PLAN SELECT * FROM episode_sources WHERE tmdb=515151 AND season=1 AND episode=1",
            ).forEach { sql ->
                db.rawQuery(sql, null).use { cursor ->
                    while (cursor.moveToNext()) result += (0 until cursor.columnCount).joinToString(" ") { cursor.getString(it) }
                }
            }
            result
        }
    }
}
