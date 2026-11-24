// FILE GUIDE: Draws the custom bottom navigation bar and dispatches destination and posted-items actions.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nearnest.R

/** Map-home navigation bar with a raised center add button. */
class BottomNavBar(
    context: Context,
    private val onDestinationSelected: (Destination) -> Unit = {},
    private val onMyItemsClick: () -> Unit = {}
) : FrameLayout(context) {
    /** Implements the app bottom navigation UI. */
    enum class Destination { HOME, CHAT, NOTIFICATIONS, PROFILE }

    val preferredHeightDp: Int = if (
        resources.configuration.screenHeightDp < 500 ||
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) 82 else 100

    private val destinations = mutableListOf<NavigationItem>()
    private var selectedDestination = Destination.HOME
    private val activeColor = Color.rgb(18, 165, 105)
    private val inactiveColor = Color.rgb(176, 178, 180)
    private val compact = preferredHeightDp == 82
    private val buttonSizeDp = if (compact) 48 else 58
    private val notchTopDp = if (compact) 24 else 31
    private val notchBottomDp = if (compact) 54 else 65

    init {
        clipChildren = false
        clipToPadding = false
        addView(BarSurface(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(
            Triple(Destination.HOME, R.drawable.ic_nav_home, "Home"),
            Triple(Destination.CHAT, R.drawable.ic_nav_chat, "Chat"),
            null,
            Triple(Destination.NOTIFICATIONS, R.drawable.ic_nav_notifications, "Notifications"),
            Triple(Destination.PROFILE, R.drawable.ic_nav_profile, "Profile")
        )
        items.forEach { item ->
            val slot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            if (item != null) {
                val (destination, iconResource, labelText) = item
                val icon = ImageView(context).apply {
                    setImageResource(iconResource)
                    contentDescription = labelText
                }
                val badge = TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 9f
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    setPadding(dp(3), 0, dp(3), 0)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.rgb(221, 59, 59))
                    }
                    visibility = View.GONE
                    contentDescription = "Unread notifications"
                }
                val label = TextView(context).apply {
                    text = labelText
                    textSize = if (compact) 9f else 10f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                val iconContainer = FrameLayout(context).apply {
                    clipChildren = false
                    clipToPadding = false
                    addView(icon, LayoutParams(dp(23), dp(23), Gravity.CENTER))
                    addView(badge, LayoutParams(dp(17), dp(17), Gravity.TOP or Gravity.END).apply {
                        topMargin = -dp(3)
                        marginEnd = -dp(4)
                    })
                }
                slot.addView(iconContainer, LinearLayout.LayoutParams(dp(30), dp(25)))
                slot.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                slot.contentDescription = labelText
                slot.isClickable = true
                slot.isFocusable = true
                slot.setOnClickListener { select(destination) }
                destinations.add(NavigationItem(destination, icon, label, badge))
            }
            row.addView(slot, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        val screenWidthDp = resources.configuration.screenWidthDp
        val rowWidth = if (screenWidthDp > 760) dp(760) else LayoutParams.MATCH_PARENT
        addView(row, LayoutParams(rowWidth, dp(if (compact) 54 else 64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        val addButton = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 166, 103))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Sell an item"
            isClickable = true
            isFocusable = true
            setOnClickListener { onMyItemsClick() }
        }
        val cartIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_nav_cart)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = null
        }
        addButton.addView(cartIcon, LayoutParams(dp(if (compact) 24 else 28), dp(if (compact) 24 else 28), Gravity.CENTER))
        addView(addButton, LayoutParams(dp(buttonSizeDp), dp(buttonSizeDp), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(2)
        })
        addView(TextView(context).apply {
            text = "My items"
            textSize = if (compact) 9f else 10f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(activeColor)
            contentDescription = "My posted items"
        }, LayoutParams(dp(60), dp(16), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(buttonSizeDp + 1)
        })

        select(Destination.HOME)
    }

    /** Updates selected navigation styling and notifies the destination listener. */
    private fun select(destination: Destination) {
        selectedDestination = destination
        destinations.forEach { item ->
            val selected = item.destination == selectedDestination
            val color = if (selected) activeColor else inactiveColor
            item.icon.imageTintList = ColorStateList.valueOf(color)
            item.label.setTextColor(color)
            item.label.visibility = if (selected) View.VISIBLE else View.GONE
        }
        onDestinationSelected(destination)
    }

    /** Selects a bottom navigation destination and updates its appearance. */
    fun selectDestination(destination: Destination) = select(destination)

    /** Updates the unread notification badge without changing the selected tab. */
    fun setNotificationCount(count: Int) {
        val badge = destinations.firstOrNull { it.destination == Destination.NOTIFICATIONS }?.badge ?: return
        badge.text = if (count > 9) "9+" else count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Implements the app bottom navigation UI. */

    private data class NavigationItem(
        val destination: Destination,
        val icon: ImageView,
        val label: TextView,
        val badge: TextView
    )

    private inner class BarSurface(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(dp(6).toFloat(), 0f, -dp(2).toFloat(), Color.argb(34, 24, 48, 72))
        }

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        /** Draws the white bar surface with the raised center-action notch. */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val center = width / 2f
            val top = dp(notchTopDp).toFloat()
            val notchBottom = dp(notchBottomDp).toFloat()
            val shoulder = dp(buttonSizeDp + 2).toFloat()
            val control = dp(if (compact) 31 else 38).toFloat()
            val path = Path().apply {
                moveTo(0f, top)
                lineTo(center - shoulder, top)
                cubicTo(center - control, top, center - control, notchBottom, center, notchBottom)
                cubicTo(center + control, notchBottom, center + control, top, center + shoulder, top)
                lineTo(width.toFloat(), top)
                lineTo(width.toFloat(), height.toFloat())
                lineTo(0f, height.toFloat())
                close()
            }
            canvas.drawPath(path, paint)
        }
    }
}
