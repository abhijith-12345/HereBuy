// FILE GUIDE: Stores local alert preferences and fetches newly posted live listings within the chosen radius.
// Change this file for alert polling, radius persistence, inbox retention, or unread-count behavior.

package com.nearnest.data.remote

import android.content.Context
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/** Provides the notification radius, incremental alert API call, and locally cached in-app inbox. */
class ListingAlertRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listingSerializer = ListSerializer(NearbyListing.serializer())
    private val stringSerializer = ListSerializer(String.serializer())

    /** Returns the saved nearby alert radius in kilometers. */
    fun getRadiusKm(): Int = preferences.getInt(KEY_RADIUS_KM, DEFAULT_RADIUS_KM)

    /** Saves a supported alert radius in kilometers. */
    fun setRadiusKm(radiusKm: Int) {
        preferences.edit().putInt(KEY_RADIUS_KM, radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)).apply()
    }

    /** Returns cached alert cards, newest first. */
    fun getAlerts(): List<NearbyListing> = runCatching {
        val encoded = preferences.getString(KEY_ALERTS, null) ?: return emptyList()
        json.decodeFromString(listingSerializer, encoded)
            .sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    /** Returns the number of cached alerts the user has not opened yet. */
    fun getUnreadCount(): Int = readUnreadIds().size

    /** Marks all currently cached alerts as read. */
    fun markRead() {
        preferences.edit().remove(KEY_UNREAD_IDS).apply()
    }

    /** Fetches newly live products around the supplied current device location and updates the local inbox. */
    suspend fun checkForNewListings(
        accessToken: String,
        latitude: Double,
        longitude: Double
    ): Int {
        val radiusMeters = getRadiusKm() * 1_000
        val since = preferences.getString(KEY_CHECKED_AT, null)
        val sinceQuery = since?.let { "&since=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val url = "${ApiClientProvider.baseUrl}/listings/alerts" +
            "?latitude=$latitude&longitude=$longitude&radius_meters=$radiusMeters$sinceQuery"
        val response = ApiClientProvider.client.get(url) {
            bearerAuth(accessToken)
        }
        response.requireSuccess()
        val result = response.body<ListingAlertsResponse>()
        cacheNewAlerts(result)
        return getUnreadCount()
    }

    /** Stores new alerts, marks just-arrived listing IDs unread, and advances the server-issued cursor. */
    private fun cacheNewAlerts(result: ListingAlertsResponse) {
        val cached = getAlerts()
        val cachedIds = cached.mapTo(mutableSetOf()) { it.id }
        val newListings = result.listings.filterNot { it.id in cachedIds }
        val merged = (newListings + cached).distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(MAX_CACHED_ALERTS)
        val unreadIds = (readUnreadIds() + newListings.map { it.id }).toSet()
        preferences.edit()
            .putString(KEY_ALERTS, json.encodeToString(listingSerializer, merged))
            .putString(KEY_UNREAD_IDS, json.encodeToString(stringSerializer, unreadIds.toList()))
            .putString(KEY_CHECKED_AT, result.checkedAt)
            .apply()
    }

    /** Reads the local unread listing IDs, treating corrupt preference data as an empty set. */
    private fun readUnreadIds(): Set<String> = runCatching {
        val encoded = preferences.getString(KEY_UNREAD_IDS, null) ?: return emptySet()
        json.decodeFromString(stringSerializer, encoded).toSet()
    }.getOrDefault(emptySet())

    private companion object {
        const val PREFERENCES_NAME = "herebuy_listing_alerts"
        const val KEY_RADIUS_KM = "radius_km"
        const val KEY_ALERTS = "alerts"
        const val KEY_UNREAD_IDS = "unread_ids"
        const val KEY_CHECKED_AT = "checked_at"
        const val DEFAULT_RADIUS_KM = 5
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 100
        const val MAX_CACHED_ALERTS = 100
    }
}
