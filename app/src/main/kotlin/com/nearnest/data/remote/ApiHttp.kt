// FILE GUIDE: Defines the API error type and converts unsuccessful HTTP responses into readable exceptions.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Represents a backend error response and preserves its HTTP status when available. */

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

/** Returns for success responses; otherwise parses the detail and throws ApiException. */
suspend fun HttpResponse.requireSuccess() {
    if (status.isSuccess()) return
    val responseText = bodyAsText()
    val detail = runCatching {
        Json.parseToJsonElement(responseText).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    throw ApiException(detail ?: "API request failed (${status.value}).", status.value)
}
