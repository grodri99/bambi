package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestRepositoryImplTest {

    private lateinit var context: Context
    private val kid = Profile(id = "kid", name = "Niño", avatar = 1, kids = true)

    @Before
    fun clearRequests() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("title_requests", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun repository(myList: MyListRepository = mockk(relaxed = true)): RequestRepositoryImpl {
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(kid)
            every { profiles } returns MutableStateFlow(listOf(Profile(id = "admin", name = "Admin", avatar = 0), kid))
        }
        return RequestRepositoryImpl(context, profiles, myList)
    }

    @Test
    fun `request is stored per profile and does not duplicate`() {
        val repository = repository()
        val item = CatalogItem("42", null, MediaType.MOVIE, "Movie", "", "poster", null, 2020, 8f)

        repository.request(item)
        repository.request(item)

        val request = repository.requests.value.single()
        assertEquals("kid", request.profileId)
        assertEquals("Movie", request.title)
        assertEquals("poster", request.posterUrl)
    }

    @Test
    fun `approving adds the title to the requester's list and clears the request`() {
        val myList = mockk<MyListRepository>(relaxed = true)
        val repository = repository(myList)
        repository.request(CatalogItem("42", null, MediaType.MOVIE, "Movie", "", null, null, 2020, 8f))

        repository.approve(repository.requests.value.single().id)

        verify(exactly = 1) { myList.addFor("kid", MediaType.MOVIE, "42") }
        assertTrue(repository.requests.value.isEmpty())
        assertTrue(repository.isApproved(CatalogItem("42", null, MediaType.MOVIE, "Movie", "", null, null, 2020, 8f)))
    }

    @Test
    fun `adult profiles cannot request`() {
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(Profile(id = "adult", name = "Adulto", avatar = 0))
            every { this@mockk.profiles } returns MutableStateFlow(emptyList())
        }
        val repository = RequestRepositoryImpl(context, profiles, mockk(relaxed = true))
        repository.request(CatalogItem("42", null, MediaType.MOVIE, "Movie", "", null, null, 2020, 8f))
        assertTrue(repository.requests.value.isEmpty())
    }

    @Test
    fun `dismiss removes only that request`() {
        val repository = repository()
        repository.request(CatalogItem("42", null, MediaType.MOVIE, "One", "", null, null, 2020, 8f))
        repository.request(CatalogItem("43", null, MediaType.MOVIE, "Two", "", null, null, 2021, 8f))

        repository.dismiss(repository.requests.value.first().id)

        assertEquals(1, repository.requests.value.size)
    }
}
