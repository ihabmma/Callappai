package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPartDto(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContentDto(
    val role: String? = null,
    val parts: List<GeminiPartDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfigDto(
    val temperature: Double? = 0.65,
    val maxOutputTokens: Int? = 450,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequestDto(
    val systemInstruction: GeminiContentDto? = null,
    val contents: List<GeminiContentDto>,
    val generationConfig: GeminiGenerationConfigDto? = GeminiGenerationConfigDto()
)

@JsonClass(generateAdapter = true)
data class GeminiCandidateDto(
    val content: GeminiContentDto? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto>? = null,
    val error: GeminiErrorDto? = null
)

interface GeminiRestApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequestDto
    ): Response<GeminiResponseDto>

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        fun create(): GeminiRestApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GeminiRestApi::class.java)
        }
    }
}
