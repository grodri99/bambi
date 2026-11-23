package com.shieldstream.tv.network.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AllDebridApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `magnet status accepts a single object`() {
        // Lo que devuelve v4.1/magnet/status con un id (el error del issue: «Expected start of the array»).
        val data = json.decodeFromString(
            AllDebridStatusDataDto.serializer(),
            """{"magnets":{"id":861364001,"filename":"eb.mkv","status":"Ready","statusCode":4}}""",
        )
        assertEquals(listOf(AllDebridStatusDto(id = 861364001, status = "Ready", statusCode = 4)), data.magnets)
    }

    @Test
    fun `magnet status still accepts a list`() {
        val data = json.decodeFromString(
            AllDebridStatusDataDto.serializer(),
            """{"magnets":[{"id":1,"status":"Downloading","statusCode":1},{"id":2,"status":"Ready","statusCode":4}]}""",
        )
        assertEquals(listOf(1L, 2L), data.magnets.map { it.id })
    }

    @Test
    fun `uploads and files accept both shapes`() {
        assertEquals(7L, json.decodeFromString(AllDebridUploadDataDto.serializer(), """{"magnets":{"id":7,"ready":true}}""").magnets.single().id)
        assertEquals(1, json.decodeFromString(AllDebridFilesDataDto.serializer(), """{"magnets":[{"files":[{"n":"a.mkv","s":1}]}]}""").magnets.size)
        assertEquals(0, json.decodeFromString(AllDebridFilesDataDto.serializer(), """{}""").magnets.size)
    }
}
