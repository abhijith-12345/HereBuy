package com.nearnest.data.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

suspend fun HttpResponse.requireSuccess() {
    if (status.isSuccess()) return
    val responseText = bodyAsText()
    val detail = runCatching {
        Json.parseToJsonElement(responseText).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    throw ApiException(detail ?: "API request failed (${status.value}).", status.value)
}
