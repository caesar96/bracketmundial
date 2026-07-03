package com.example.bracketmundial.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Cliente para el JSON estático de openfootball (fallback sin key); el baseUrl es un
 *  relleno válido ya que el @GET de OpenFootballService usa una URL absoluta. */
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
