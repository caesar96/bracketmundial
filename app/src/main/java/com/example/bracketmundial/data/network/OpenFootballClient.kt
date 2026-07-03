package com.example.bracketmundial.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Client for openfootball's static JSON (keyless fallback); the baseUrl is just a
 *  valid placeholder since OpenFootballService's @GET uses an absolute URL. */
object OpenFootballClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder().build()

    val service: OpenFootballService by lazy {
        Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFootballService::class.java)
    }
}
