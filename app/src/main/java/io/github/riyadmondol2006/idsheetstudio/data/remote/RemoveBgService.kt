package io.github.riyadmondol2006.idsheetstudio.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.IOException

interface RemoveBgApi {
    @Multipart
    @POST("v1.0/removebg")
    suspend fun removeBackground(
        @Header("X-Api-Key") apiKey: String,
        @Part image: MultipartBody.Part,
        @Part("size") size: RequestBody,
        @Part("format") format: RequestBody,
        @Part("type") type: RequestBody,
    ): Response<ResponseBody>

    @GET("v1.0/account")
    suspend fun account(@Header("X-Api-Key") apiKey: String): Response<ResponseBody>
}

data class RemoveBgAccount(val totalCredits: Double, val freeCalls: Int)

/** User-facing failure of a remove.bg request. */
class RemoveBgException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { INVALID_KEY, NO_CREDITS, RATE_LIMITED, BAD_IMAGE, NETWORK, SERVER, NO_KEY }
}

class RemoveBgService(private val api: RemoveBgApi) {

    /** Removes the background from [jpeg] and returns a transparent PNG. */
    suspend fun removeBackground(apiKey: String, jpeg: ByteArray, size: String): ByteArray {
        val part = MultipartBody.Part.createFormData(
            "image_file",
            "photo.jpg",
            jpeg.toRequestBody("image/jpeg".toMediaType()),
        )
        val text = "text/plain".toMediaType()
        val response = call {
            api.removeBackground(apiKey, part, size.toRequestBody(text), "png".toRequestBody(text), "auto".toRequestBody(text))
        }
        val body = response.body()
        if (response.isSuccessful && body != null) {
            val bytes = body.bytes()
            if (bytes.size < 8 || bytes[1] != 'P'.code.toByte()) throw RemoveBgException(RemoveBgException.Kind.SERVER, "Unexpected response from remove.bg")
            return bytes
        }
        throw errorFor(response)
    }

    /** Validates a key and returns the account balance. Does not consume credits. */
    suspend fun account(apiKey: String): RemoveBgAccount {
        val response = call { api.account(apiKey) }
        val body = response.body()
        if (!response.isSuccessful || body == null) throw errorFor(response)
        val root = Network.json.parseToJsonElement(body.string()).jsonObject
        val attributes = root["data"]?.jsonObject?.get("attributes")?.jsonObject
        val credits = attributes?.get("credits")?.jsonObject?.get("total")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val freeCalls = attributes?.get("api")?.jsonObject?.get("free_calls")?.jsonPrimitive?.intOrNull ?: 0
        return RemoveBgAccount(credits, freeCalls)
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): Response<T> = try {
        block()
    } catch (e: IOException) {
        throw RemoveBgException(RemoveBgException.Kind.NETWORK, "Network error. Check your connection and try again.")
    }

    private fun errorFor(response: Response<*>): RemoveBgException {
        val apiMessage = runCatching {
            val text = response.errorBody()?.string().orEmpty()
            val errors = (Network.json.parseToJsonElement(text) as JsonObject)["errors"]?.jsonArray
            errors?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (response.code()) {
            400 -> RemoveBgException(RemoveBgException.Kind.BAD_IMAGE, apiMessage ?: "remove.bg could not process this photo.")
            401, 403 -> RemoveBgException(RemoveBgException.Kind.INVALID_KEY, "remove.bg rejected this API key. Check that it was copied correctly.")
            402 -> RemoveBgException(RemoveBgException.Kind.NO_CREDITS, "Your remove.bg account has no credits left.")
            429 -> RemoveBgException(RemoveBgException.Kind.RATE_LIMITED, "Too many requests. Please wait a moment and retry.")
            else -> RemoveBgException(RemoveBgException.Kind.SERVER, apiMessage ?: "remove.bg error (HTTP ${response.code()}).")
        }
    }

    companion object {
        const val BASE_URL = "https://api.remove.bg/"
        const val UPLOAD_MAX_DIMENSION = 2500
    }
}
