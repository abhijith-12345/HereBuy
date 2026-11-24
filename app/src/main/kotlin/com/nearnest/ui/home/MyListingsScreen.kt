// FILE GUIDE: Loads the signed-in seller listings and renders loading, empty, error, and listing-card states.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.home

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.data.remote.AuthRepository
import com.nearnest.data.remote.ListingItem
import com.nearnest.data.remote.MyListingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Implements the seller posted-items screen. */

class MyListingsScreen(
    private val activity: Activity,
    private val onSellNew: () -> Unit,
    private val onClose: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MyListingsRepository()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var addButton: Button

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(): View {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 249, 252))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            setBackgroundColor(Color.WHITE)
        }
        header.addView(TextView(activity).apply {
            text = "My posted items"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(34, 55, 73))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = "Back"
            textSize = 14f
            setTextColor(Color.rgb(24, 130, 91))
            setPadding(dp(12), dp(10), 0, dp(10))
            setOnClickListener { onClose() }
        })
        page.addView(header, LinearLayout.LayoutParams(-1, -2))

        addButton = Button(activity).apply {
            text = "Sell a new item"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(20, 166, 103))
            setOnClickListener { onSellNew() }
        }
        status = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.rgb(83, 101, 117))
            setPadding(dp(20), dp(20), dp(20), dp(12))
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
        page.addView(addButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(dp(16), dp(8), dp(16), dp(12))
        })
        load()
        return page
    }

    /** Cancels this screen's coroutine scope and releases its pending work. */
    fun onDestroy() = scope.cancel()

    /** Reloads the current screen's remote or account data. */
    fun refresh() = load()

    /** Loads remote data and updates the screen's loading, error, and result states. */
    private fun load() {
        if (!::status.isInitialized) return
        status.text = "Loading your posted items…"
        content.removeAllViews()
        addButton.isEnabled = false
        val token = AuthRepository(activity).getAccessToken()
        if (token.isNullOrBlank()) {
            status.text = "Please sign in again to view your items."
            addButton.isEnabled = true
            return
        }
        scope.launch {
            try {
                val listings = repository.getMine(token)
                render(listings)
            } catch (error: Exception) {
                status.text = error.localizedMessage ?: "Could not load your items. Tap to retry."
                status.setOnClickListener { load() }
            } finally {
                addButton.isEnabled = true
            }
        }
    }

    /** Displays the supplied records in the screen's content area. */
    private fun render(listings: List<ListingItem>) {
        status.setOnClickListener(null)
        if (listings.isEmpty()) {
            status.text = "You haven't posted any items yet. Sell your first item to see it here."
            return
        }
        status.text = "${listings.size} ${if (listings.size == 1) "item" else "items"} posted"
        listings.forEach { listing ->
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
                text = "₹${"%.2f".format(listing.price)}  ·  ${listing.category}  ·  ${listing.status}"
                textSize = 14f
                setTextColor(Color.rgb(24, 130, 91))
                setPadding(0, dp(6), 0, 0)
            })
            if (listing.description.isNotBlank()) card.addView(TextView(activity).apply {
                text = listing.description
                textSize = 14f
                setTextColor(Color.rgb(83, 101, 117))
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
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
