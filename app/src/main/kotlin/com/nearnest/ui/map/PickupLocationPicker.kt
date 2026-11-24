// FILE GUIDE: Lets a seller choose a fixed pickup point by moving the map beneath a center pin.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.map

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nearnest.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/** Implements the seller pickup-point map picker. */

data class PickupPoint(val latitude: Double, val longitude: Double)

/** Lets the seller choose a fixed pickup point by moving the map under its center marker. */
class PickupLocationPicker(
    private val activity: Activity,
    private val initialPoint: PickupPoint?,
    private val onSelected: (PickupPoint) -> Unit,
    private val onCancel: () -> Unit
) {
    private lateinit var mapView: MapView
    private var selectedPoint: PickupPoint? = null
    private var destroyed = false

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(): View {
        MapLibre.getInstance(activity)
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        mapView = MapView(activity)
        mapView.onCreate(null)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedCard()
            elevation = dp(5).toFloat()
        }
        header.addView(TextView(activity).apply {
            text = "Choose pickup spot"
            textSize = 18f
            setTextColor(Color.rgb(35, 51, 66))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(activity).apply {
            text = "Move the map until the marker is on the pickup point."
            textSize = 13f
            setTextColor(Color.rgb(95, 111, 126))
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(16), dp(16), dp(16), 0)
        })

        root.addView(CenterPin(activity), FrameLayout.LayoutParams(dp(44), dp(52), Gravity.CENTER))

        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            background = roundedCard()
            elevation = dp(6).toFloat()
        }
        footer.addView(TextView(activity).apply {
            text = "Only this pickup spot will be shared with buyers."
            textSize = 13f
            setTextColor(Color.rgb(80, 98, 114))
        })
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cancelButton = Button(activity).apply {
            text = "Cancel"
            setTextColor(Color.rgb(79, 94, 108))
            setOnClickListener { onCancel() }
        }
        val useButton = Button(activity).apply {
            text = "Use this pickup spot"
            isEnabled = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(20, 166, 103))
            setOnClickListener { selectedPoint?.let(onSelected) }
        }
        actions.addView(cancelButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginEnd = dp(8)
        })
        actions.addView(useButton, LinearLayout.LayoutParams(0, dp(50), 2f))
        footer.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        root.addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        root.setOnApplyWindowInsetsListener { _, insets ->
            val (topInset, bottomInset) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                systemBars.top to systemBars.bottom
            } else {
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            val headerParams = header.layoutParams as FrameLayout.LayoutParams
            headerParams.topMargin = topInset + dp(16)
            header.layoutParams = headerParams
            val footerParams = footer.layoutParams as FrameLayout.LayoutParams
            footerParams.bottomMargin = bottomInset + dp(12)
            footer.layoutParams = footerParams
            insets
        }

        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            map.setStyle("asset://map/nearnest-map-style.json") {
                val start = initialPoint ?: PickupPoint(12.9716, 77.5946)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(start.latitude, start.longitude))
                    .zoom(15.0)
                    .build()
                /** Updates the pickup coordinate from the map camera center. */
                fun updateSelectedPoint() {
                    map.cameraPosition.target?.let { target ->
                        selectedPoint = PickupPoint(target.latitude, target.longitude)
                        useButton.isEnabled = true
                    }
                }
                map.addOnCameraIdleListener { updateSelectedPoint() }
                updateSelectedPoint()
            }
        }
        return root
    }

    /** Forwards Android start lifecycle to owned map views. */
    fun onStart() { if (::mapView.isInitialized && !destroyed) mapView.onStart() }
    /** Resumes owned map views and location updates. */
    fun onResume() { if (::mapView.isInitialized && !destroyed) mapView.onResume() }
    /** Pauses owned map views and location updates. */
    fun onPause() { if (::mapView.isInitialized && !destroyed) mapView.onPause() }
    /** Stops owned map views and location updates. */
    fun onStop() { if (::mapView.isInitialized && !destroyed) mapView.onStop() }
    /** Forwards Android low-memory notification to the map view. */
    fun onLowMemory() { if (::mapView.isInitialized && !destroyed) mapView.onLowMemory() }
    /** Saves state needed to restore the current view. */
    fun onSaveInstanceState(outState: Bundle) {
        if (::mapView.isInitialized && !destroyed) mapView.onSaveInstanceState(outState)
    }
    /** Releases resources and cancels work owned by this screen. */
    fun onDestroy() {
        if (::mapView.isInitialized && !destroyed) {
            destroyed = true
            mapView.onDestroy()
        }
    }

    /** Creates the shared rounded card background. */
    private fun roundedCard() = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(224, 232, 239))
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    /** Implements the seller pickup-point map picker. */

    private class CenterPin(context: Activity) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        /** Draws the fixed center pin used to mark the currently selected pickup coordinate. */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f - dp(6)
            paint.color = Color.WHITE
            paint.setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), Color.argb(75, 0, 0, 0))
            canvas.drawCircle(cx, cy, dp(12).toFloat(), paint)
            paint.clearShadowLayer()
            paint.color = Color.rgb(20, 166, 103)
            canvas.drawCircle(cx, cy, dp(7).toFloat(), paint)
            paint.color = Color.rgb(20, 166, 103)
            paint.strokeWidth = dp(3).toFloat()
            canvas.drawLine(cx, cy + dp(12), cx, cy + dp(24), paint)
        }

        /** Converts a density-independent pixel value to screen pixels. */
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    }
}
