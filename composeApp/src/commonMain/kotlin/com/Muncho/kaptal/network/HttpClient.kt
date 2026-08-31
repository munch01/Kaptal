package com.muncho.kaptal.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

import io.ktor.client.plugins.*
import io.ktor.client.request.*

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
    install(DefaultRequest) {
        header("User-Agent", "Kaptal/2.0.0")
    }
}
