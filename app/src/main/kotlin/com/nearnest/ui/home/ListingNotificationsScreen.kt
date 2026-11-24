// FILE GUIDE: Displays cached in-app alerts for newly posted live listings near the user's current location.
// Change this file to adjust notification inbox layout, empty states, or alert card presentation.

package com.nearnest.ui.home

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.data.remote.ListingAlertRepository
import com.nearnest.data.remote.NearbyListing

/** Builds the in-app notification center for new live listings within the chosen radius. */
class ListingNotificationsScreen(
    private val activity: Activity,
    private val repository: ListingAlertRepository
) {
    private lateinit var status: TextView
    private lateinit var content: LinearLayout

    /** Builds and returns the notification center view hierarchy. */
    fun createView(): View {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 249, 252))
        }
        page.addView(TextView(activity).apply {
            text = "Notifications"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(34, 55, 73))
            setPadding(dp(20), dp(18), dp(20), dp(14))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, -2))

        status = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(83, 101, 117))
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        page.addView(status, LinearLayout.LayoutParams(-1, -2))
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(20))
        }
        page.addView(ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        render(locationAvailable = true)
        return page
    }

    /** Marks cached alerts as read when the user opens the notifications tab. */
    fun open(locationAvailable: Boolean) {
        repository.markRead()
        render(locationAvailable)
    }

    /** Refreshes displayed alerts after a foreground poll without marking new alerts read. */
    fun refresh(locationAvailable: Boolean) = render(locationAvailable)

    /** Renders the location hint, selected radius, and cached alert cards. */
    private fun render(locationAvailable: Boolean) {
        if (!::status.isInitialized) return
        content.removeAllViews()
        val radiusKm = repository.getRadiusKm()
        if (!locationAvailable) {
            status.text = "Allow location access to receive alerts within $radiusKm km."
            return
        }

        val alerts = repository.getAlerts()
        status.text = "New live listings within $radiusKm km"
        if (alerts.isEmpty()) {
            content.addView(TextView(activity).apply {
                text = "No new nearby item alerts yet. New available listings will appear here."
                textSize = 15f
                setTextColor(Color.rgb(83, 101, 117))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }
        alerts.forEach(::addAlertCard)
    }

    /** Adds one newly posted listing to the notification feed. */
    private fun addAlertCard(listing: NearbyListing) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(226, 232, 237))
            }
        }
        card.addView(TextView(activity).apply {
            text = listing.title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(34, 55, 73))
        })
        card.addView(TextView(activity).apply {
            text = "₹${"%.2f".format(listing.price)}  ·  ${listing.category}  ·  ${"%.1f".format(listing.distanceMeters / 1_000.0)} km away"
            textSize = 14f
            setTextColor(Color.rgb(24, 130, 91))
            setPadding(0, dp(6), 0, 0)
        })
        if (listing.pickupArea.isNotBlank()) card.addView(TextView(activity).apply {
            text = "Pickup: ${listing.pickupArea}"
            textSize = 13f
            setTextColor(Color.rgb(108, 122, 135))
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
