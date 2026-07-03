package com.example.bracketmundial.data.network

import com.example.bracketmundial.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Cliente HTTP para football-data.org v4; requiere token gratuito (header X-Auth-Token). */
object FootballDataClient {
    private const val BASE_URL = "https://api.football-data.org/v4/"

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-Auth-Token", BuildConfig.FOOTBALLDATA_TOKEN)
                .build()
            chain.proceed(request)
        }
        .build()

    val service: FootballDataService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FootballDataService::class.java)
    }
}
