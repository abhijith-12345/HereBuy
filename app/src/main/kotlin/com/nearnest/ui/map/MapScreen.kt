// FILE GUIDE: Owns the map viewport, optional location access, visible listing markers, and map lifecycle.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.map

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.Button
import com.nearnest.data.remote.NearbyListing
import java.text.NumberFormat
import java.util.Locale
import android.content.pm.PackageManager
import android.location.Location
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.nearnest.R
import com.nearnest.data.remote.ApiClientProvider
import com.nearnest.data.remote.AuthRepository
import com.nearnest.data.remote.NearbyListingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

/** Owns the map view, map style, camera, loading state, and MapView lifecycle. */
class MapScreen(private val activity: Activity) {
    private val compactControls = activity.resources.configuration.screenHeightDp < 500 ||
        activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private val locationButtonSizeDp = if (compactControls) 42 else 48
    private lateinit var mapView: MapView
    private lateinit var mapStatus: TextView
    private var myLocationButton: ImageButton? = null
    private var map: MapLibreMap? = null
    private var currentStyle: Style? = null
    private var mapStyleLoaded = false
    private var lastUserLocation: Location? = null
    private var labelsVisible = true
    private var focusOnLocationAfterPermission = false
    private val listingsRepository = NearbyListingsRepository()
    private val listingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var viewportJob: Job? = null
    private var cameraListenersAttached = false
    private val listingMarkers = mutableMapOf<Marker, NearbyListing>()
    private lateinit var mapRoot: FrameLayout
    private var listingCard: View? = null
    private val listingFormat = Locale.forLanguageTag("en-IN")
    private val prices = NumberFormat.getCurrencyInstance(listingFormat).apply {
        minimumFractionDigits = 0
    }
    var onMapBearingChanged: ((Float) -> Unit)? = null

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(savedInstanceState: Bundle?): View {
        MapLibre.getInstance(activity)

        val root = FrameLayout(activity).apply { setBackgroundColor(Color.rgb(234, 244, 252)) }
        mapRoot = root
        mapView = MapView(activity)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        mapStatus = TextView(activity).apply {
            text = "Loading map..."
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 55, 45))
                cornerRadius = dp(24).toFloat()
            }
            elevation = dp(4).toFloat()
        }
        root.addView(mapStatus, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val locationButton = ImageButton(activity).apply {
            contentDescription = "Center map on my location"
            setImageResource(R.drawable.ic_my_location)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(5).toFloat()
            setPadding(dp(if (compactControls) 9 else 12), dp(if (compactControls) 9 else 12), dp(if (compactControls) 9 else 12), dp(if (compactControls) 9 else 12))
            setOnClickListener { onMyLocationButtonClicked() }
        }
        myLocationButton = locationButton
        root.addView(
            locationButton,
            FrameLayout.LayoutParams(dp(locationButtonSizeDp), dp(locationButtonSizeDp), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(16)
                bottomMargin = dp(116)
            }
        )

        mapView.addOnDidFinishLoadingStyleListener {
            activity.runOnUiThread { mapStatus.text = "Map style loaded; loading streets..." }
        }
        mapView.addOnDidFinishLoadingMapListener {
            activity.runOnUiThread {
                mapStatus.visibility = View.GONE
            }
        }
        mapView.addOnDidFailLoadingMapListener { errorMessage ->
            activity.runOnUiThread {
                mapStatus.visibility = View.VISIBLE
                mapStatus.text = "Map failed to load. Tap to retry."
                mapStatus.contentDescription = errorMessage
                mapStatus.setOnClickListener { loadMap() }
            }
        }

        loadMap()
        return root
    }

    /** Loads the map style, resets the initial camera, and starts location permission handling. */
    private fun loadMap() {
        mapStatus.visibility = View.VISIBLE
        mapStatus.text = "Loading map..."
        mapView.getMapAsync { map ->
            this.map = map
            map.uiSettings.isCompassEnabled = false
            map.setOnMarkerClickListener { marker ->
                val listing = listingMarkers[marker]
                if (listing == null) false else {
                    showListingCard(listing, marker.position)
                    true
                }
            }
            if (!cameraListenersAttached) {
                map.addOnMapClickListener {
                    dismissListingCard()
                    false
                }
                map.addOnCameraMoveListener {
                    dismissListingCard()
                    viewportJob?.cancel()
                    onMapBearingChanged?.invoke(map.cameraPosition.bearing.toFloat())
                }
                map.addOnCameraIdleListener {
                    if (mapStyleLoaded) loadListingsInViewport()
                }
                cameraListenersAttached = true
            }
            map.setStyle(MAP_STYLE_URL) { style ->
                mapStyleLoaded = true
                currentStyle = style
                setLabelsVisible(labelsVisible)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(12.9716, 77.5946))
                    .zoom(13.0)
                    .build()
                requestLocationAccess(focusOnLocationAfterPermission)
                loadListingsInViewport()
            }
        }
    }

    /** Requests location permission and optionally focuses the map on the user. */
    private fun requestLocationAccess(focus: Boolean = false) {
        if (hasLocationPermission()) {
            showUserLocation(focus)
            if (focus) focusOnLocationAfterPermission = false
        } else {
            activity.requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    /** Handles the result of the device location permission prompt. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST || !mapStyleLoaded) return

        if (hasLocationPermission()) {
            showUserLocation(focus = focusOnLocationAfterPermission)
            focusOnLocationAfterPermission = false
        } else {
            // Location improves recentering; viewport browsing still works without it.
            mapStatus.visibility = View.GONE
        }
    }

    /** Checks whether precise or approximate location access is granted. */
    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Handles a tap on the map's current-location control. */
    private fun onMyLocationButtonClicked() {
        focusOnLocationAfterPermission = true
        if (mapStyleLoaded) requestLocationAccess(focus = true)
    }

    @Suppress("MissingPermission")
    /** Reads the device location, updates the location marker, and optionally moves the camera. */
    private fun showUserLocation(focus: Boolean = false) {
        val currentMap = map ?: return
        val style = currentStyle ?: return
        try {
            val locationComponent = currentMap.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(activity, style).build()
            )
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.NORMAL
            locationComponent.cameraMode = CameraMode.TRACKING
            val lastLocation = locationComponent.lastKnownLocation
            if (lastLocation != null) lastUserLocation = lastLocation
            if (focus) {
                if (lastLocation != null) {
                    currentMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lastLocation.latitude, lastLocation.longitude),
                            USER_LOCATION_ZOOM
                        ),
                        500
                    )
                } else {
                    currentMap.animateCamera(CameraUpdateFactory.zoomTo(USER_LOCATION_ZOOM), 500)
                }
            }
            mapStatus.visibility = View.GONE
        } catch (error: Exception) {
            mapStatus.visibility = View.VISIBLE
            mapStatus.text = "Could not show current location"
            mapStatus.contentDescription = error.message
        }
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    /** Debounces camera changes, fetches every listing in the visible bounds, and replaces markers. */
    private fun loadListingsInViewport() {
        if (!ApiClientProvider.isConfigured) return
        val accessToken = AuthRepository(activity).getAccessToken() ?: return
        viewportJob?.cancel()
        viewportJob = listingsScope.launch {
            try {
                delay(VIEWPORT_REQUEST_DEBOUNCE_MS)
                val currentMap = map ?: return@launch
                val bounds = currentMap.projection.visibleRegion.latLngBounds
                val center = currentMap.cameraPosition.target ?: return@launch
                val listings = listingsRepository.getInViewport(
                    accessToken = accessToken,
                    west = bounds.longitudeWest,
                    south = bounds.latitudeSouth,
                    east = bounds.longitudeEast,
                    north = bounds.latitudeNorth,
                    centerLatitude = center.latitude,
                    centerLongitude = center.longitude
                )
                val activeMap = map ?: return@launch
                listingMarkers.keys.forEach(activeMap::removeMarker)
                listingMarkers.clear()
                listings.forEach { listing ->
                    val marker = activeMap.addMarker(
                        MarkerOptions().position(LatLng(listing.latitude, listing.longitude))
                    )
                    listingMarkers[marker] = listing
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mapStatus.visibility = View.VISIBLE
                mapStatus.text = "Could not load map listings"
                mapStatus.contentDescription = error.localizedMessage
            }
        }
    }

    private fun dismissListingCard() {
        listingCard?.let(mapRoot::removeView)
        listingCard = null
    }

    private fun showListingCard(listing: NearbyListing, position: LatLng) {
        dismissListingCard()
        val price = prices.format(listing.price)
        val distance = NumberFormat.getNumberInstance(listingFormat).apply {
            maximumFractionDigits = 1
        }.format(listing.distanceMeters / 1_000.0) + " km away"
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = listing.title
                textSize = 17f
                setTextColor(Color.rgb(15, 72, 45))
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(activity).apply {
                text = price
                textSize = 16f
                setTextColor(Color.rgb(20, 122, 67))
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(activity).apply {
                text = distance
                textSize = 13f
                setTextColor(Color.rgb(75, 97, 81))
            })
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6))
            elevation = dp(8).toFloat()
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 252, 247))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(209, 226, 211))
            }
            addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(activity).apply {
                text = "Enquire"
                isAllCaps = false
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    GradientDrawable().apply {
                        setColor(Color.rgb(20, 122, 67))
                        cornerRadius = dp(24).toFloat()
                    },
                    null
                )
                setOnClickListener {
                    val message = listOf(
                        "$price · $distance", listing.pickupArea, listing.description,
                        "Seller messaging is currently unavailable."
                    ).filter { it.isNotBlank() }.joinToString("\n\n")
                    AlertDialog.Builder(activity)
                        .setTitle(listing.title)
                        .setMessage(message)
                        .setPositiveButton("Done", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        val pin = map?.projection?.toScreenLocation(position) ?: return
        val width = minOf(dp(232), mapRoot.width - dp(16))
        if (width <= 0) return
        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        listingCard = card
        mapRoot.addView(card, FrameLayout.LayoutParams(width, -2, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = (pin.x.toInt() - width / 2).coerceIn(dp(8), mapRoot.width - width - dp(8))
            topMargin = (pin.y.toInt() - dp(36) - card.measuredHeight).coerceAtLeast(dp(8))
        })
    }

    /** Returns the current map center as a pickup coordinate when available. */
    fun currentMapCenter(): PickupPoint? {
        val target = map?.cameraPosition?.target ?: return null
        return PickupPoint(target.latitude, target.longitude)
    }

    /** Returns the last device location when location permission is available. */
    fun currentUserLocation(): PickupPoint? {
        if (!hasLocationPermission()) return null
        val location = map?.locationComponent?.lastKnownLocation ?: lastUserLocation ?: return null
        lastUserLocation = location
        return PickupPoint(location.latitude, location.longitude)
    }

    /** Reloads listing markers for the map's current visible bounds. */
    fun refreshNearbyListings() = loadListingsInViewport()

    /** Adjusts map camera padding for controls at the bottom of the screen. */
    fun setBottomObstructionPx(bottomObstructionPx: Int) {
        val button = myLocationButton ?: return
        val params = button.layoutParams as? FrameLayout.LayoutParams ?: return
        params.bottomMargin = bottomObstructionPx
        button.layoutParams = params
    }

    /** Toggles map labels without reloading the map. */
    fun setLabelsVisible(visible: Boolean) {
        labelsVisible = visible
        currentStyle?.layers
            ?.filterIsInstance<SymbolLayer>()
            ?.forEach { layer ->
                layer.setProperties(visibility(if (labelsVisible) Property.VISIBLE else Property.NONE))
            }
    }

    /** Resets the map bearing to north. */
    fun resetMapOrientation() {
        val currentMap = map ?: return
        val currentPosition = currentMap.cameraPosition
        val northUpPosition = CameraPosition.Builder()
            .target(currentPosition.target)
            .zoom(currentPosition.zoom)
            .bearing(0.0)
            .tilt(0.0)
            .build()
        currentMap.animateCamera(CameraUpdateFactory.newCameraPosition(northUpPosition), 350)
    }

    /** Forwards Android start lifecycle to owned map views. */
    fun onStart() = mapView.onStart()
    /** Resumes owned map views and location updates. */
    fun onResume() = mapView.onResume()
    /** Pauses owned map views and location updates. */
    fun onPause() = mapView.onPause()
    /** Stops owned map views and location updates. */
    fun onStop() = mapView.onStop()
    /** Forwards Android low-memory notification to the map view. */
    fun onLowMemory() = mapView.onLowMemory()
    /** Releases resources and cancels work owned by this screen. */
    fun onDestroy() {
        viewportJob?.cancel()
        listingsScope.cancel()
        mapView.onDestroy()
    }
    /** Saves state needed to restore the current view. */
    fun onSaveInstanceState(outState: Bundle) = mapView.onSaveInstanceState(outState)

    private companion object {
        const val MAP_STYLE_URL = "asset://map/nearnest-map-style.json"
        const val LOCATION_PERMISSION_REQUEST = 4102
        const val USER_LOCATION_ZOOM = 18.0
        const val VIEWPORT_REQUEST_DEBOUNCE_MS = 100L
    }
}
