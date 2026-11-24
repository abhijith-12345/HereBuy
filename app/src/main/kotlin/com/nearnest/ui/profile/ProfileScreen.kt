// FILE GUIDE: Shows account information, privacy details, app version, and sign-out controls.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.BuildConfig
import com.nearnest.data.remote.ApiUser

/** Implements the signed-in user profile screen. */

class ProfileScreen(
    private val activity: Activity,
    private val currentUser: ApiUser,
    private val onSignOut: () -> Unit
) {
    private lateinit var emailText: TextView

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(bottomBarHeightDp: Int): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(bottomBarHeightDp + 24))
        }

        content.addView(TextView(activity).apply {
            text = "Profile"
            textSize = 26f
            setTextColor(Color.rgb(34, 50, 66))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))

        content.addView(accountCard(), cardLayout(top = 20))
        content.addView(sectionCard(
            title = "Location and privacy",
            description = "Your device location helps find nearby listings. HereBuy uses it for search and does not save it as your profile location."
        ).apply {
            addView(actionButton("Manage location permission", onClick = {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(settingsIntent)
            }), LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        }, cardLayout(top = 14))

        content.addView(sectionCard(
            title = "About HereBuy",
            description = "Neighbourhood listings, picked up locally.\nVersion ${BuildConfig.VERSION_NAME}"
        ), cardLayout(top = 14))

        content.addView(actionButton("Sign out", onSignOut, destructive = true),
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(22) })

        return ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 249, 251))
            isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
    }

    /** Reloads the current screen's remote or account data. */
    fun refresh() {
        if (!::emailText.isInitialized) return
        val accountDetails = listOfNotNull(currentUser.email, currentUser.phoneNumber).joinToString("\n")
        emailText.text = "${currentUser.username ?: currentUser.displayName}\n$accountDetails"
    }

    /** Builds the account identity card shown on the profile screen. */
    private fun accountCard(): View {
        val card = cardContainer()
        val avatar = TextView(activity).apply {
            text = "N"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 122, 67))
            }
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(54), dp(54)))
        card.addView(TextView(activity).apply {
            text = "HereBuy account"
            textSize = 17f
            setTextColor(Color.rgb(40, 52, 64))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        emailText = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.rgb(95, 111, 126))
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(emailText, LinearLayout.LayoutParams(-1, -2))
        refresh()
        return card
    }

    /** Builds a profile information card with a title and description. */
    private fun sectionCard(title: String, description: String): LinearLayout = cardContainer().apply {
        addView(TextView(activity).apply {
            text = title
            textSize = 16f
            setTextColor(Color.rgb(40, 52, 64))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        addView(TextView(activity).apply {
            text = description
            textSize = 14f
            setTextColor(Color.rgb(95, 111, 126))
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
    }

    /** Creates the shared profile card container. */
    private fun cardContainer() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(226, 232, 238))
        }
        elevation = dp(1).toFloat()
    }

    /** Builds a profile action button and applies its visual style. */
    private fun actionButton(
        label: String,
        onClick: () -> Unit,
        destructive: Boolean = false
    ) = Button(activity).apply {
        text = label
        setOnClickListener { onClick() }
        if (destructive) {
            setTextColor(Color.rgb(184, 51, 51))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 241, 241))
        } else {
            setTextColor(Color.rgb(24, 111, 180))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(236, 245, 252))
        }
    }

    /** Creates spacing parameters for a profile card. */
    private fun cardLayout(top: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
