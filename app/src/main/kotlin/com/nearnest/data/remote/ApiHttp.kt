// FILE GUIDE: Defines the API error type and converts unsuccessful HTTP responses into readable exceptions.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Represents a backend error response and preserves its HTTP status when available. */

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

/** Returns for success responses; otherwise parses the detail and throws ApiException. */
suspend fun HttpResponse.requireSuccess() {
    if (status.isSuccess()) return
    val responseText = bodyAsText()
    val detail = runCatching {
        when (val detailValue = Json.parseToJsonElement(responseText).jsonObject["detail"]) {
            is JsonPrimitive -> detailValue.contentOrNull
            is JsonArray -> detailValue.mapNotNull { issue ->
                val issueObject = issue as? JsonObject ?: return@mapNotNull null
                val message = (issueObject["msg"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val location = (issueObject["loc"] as? JsonArray)
                    ?.joinToString(".") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
                    .orEmpty()
                if (location.isBlank()) message else "$location: $message"
            }.joinToString("; ").ifBlank { null }
            else -> null
        }
    }.getOrNull()
    throw ApiException(detail ?: "API request failed (${status.value}).", status.value)
}
