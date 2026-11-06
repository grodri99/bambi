package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.AllDebridAuthPoll
import com.shieldstream.tv.domain.AllDebridAuthRepository
import com.shieldstream.tv.domain.AllDebridPin
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridLink
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import java.io.IOException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllDebridAuthRepositoryImpl @Inject constructor(
    private val api: AllDebridApi,
    private val texts: Texts,
    private val keys: ApiKeysRepository,
) : AllDebridAuthRepository {

    override suspend fun requestPin(): Result<AllDebridPin> = try {
        val response = api.getPin()
        val data = response.data ?: throw response.failure()
        Result.success(
            AllDebridPin(
                pin = data.pin,
                check = data.check,
                verificationUrl = data.user_url,
                expiresInSeconds = data.expires_in,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pollPin(pin: AllDebridPin): AllDebridAuthPoll {
        val response = try {
            api.checkPin(pin.pin, pin.check)
        } catch (_: IOException) {
            return AllDebridAuthPoll.Pending
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AllDebridAuthPoll.Failed(e.message ?: texts.get(StringsR.string.data_couldn_t_verify))
        }
        if (response.status != "success") {
            return if (response.error?.code == "PIN_EXPIRED") {
                AllDebridAuthPoll.Expired
            } else {
                AllDebridAuthPoll.Failed(
                    response.error?.message ?: texts.get(StringsR.string.settings_alldebrid_invalid_pin),
                )
            }
        }
        val data = response.data
            ?: return AllDebridAuthPoll.Failed(texts.get(StringsR.string.data_couldn_t_verify))
        if (!data.activated) return AllDebridAuthPoll.Pending
        val apiKey = data.apikey?.takeIf { it.isNotBlank() }
            ?: return AllDebridAuthPoll.Failed(texts.get(StringsR.string.data_couldn_t_verify))
        return AllDebridAuthPoll.Connected(apiKey)
    }

    override suspend fun unlock(url: String): DebridLink {
        val key = keys.keys.value[ApiService.ALLDEBRID].orEmpty()
        if (key.isBlank()) {
            return DebridLink.Problem(
                texts.get(StringsR.string.data_debrid_key_missing, DebridService.ALLDEBRID.title),
                notCached = false,
            )
        }
        return try {
            val response = api.unlock(agent = "Franklix", apiKey = key, link = url)
            val unlocked = response.data?.link.orEmpty()
            when {
                response.status == "success" && unlocked.isNotBlank() -> DebridLink.Ready(unlocked)
                else -> DebridLink.Problem(
                    response.error?.message ?: texts.get(StringsR.string.data_couldn_t_unrestrict_the_link),
                    notCached = false,
                )
            }
        } catch (_: Exception) {
            DebridLink.Problem(texts.get(StringsR.string.data_couldn_t_unrestrict_the_link), notCached = false)
        }
    }

    private fun AllDebridResponse<*>.failure(): IOException =
        IOException(error?.message ?: texts.get(StringsR.string.settings_couldn_t_connect_to_alldebrid))
}
