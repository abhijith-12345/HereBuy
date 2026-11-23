package com.nearnest.ui.map

import android.app.Activity
import android.location.Location
import android.content.pm.PackageManager
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
import com.nearnest.data.remote.NearbyListingsRepository
import com.nearnest.data.remote.SupabaseClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var locationEnabled = false
    private var labelsVisible = true
    private var focusOnLocationAfterPermission = false
    private val nearbyRepository = NearbyListingsRepository()
    private val nearbyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var nearbyJob: Job? = null
    private val listingMarkers = mutableListOf<Marker>()
    var onMapBearingChanged: ((Float) -> Unit)? = null

    fun createView(savedInstanceState: Bundle?): View {
        MapLibre.getInstance(activity)

        val root = FrameLayout(activity).apply { setBackgroundColor(Color.rgb(234, 244, 252)) }
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
                if (locationEnabled) mapStatus.visibility = View.GONE
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

    private fun loadMap() {
        mapStatus.visibility = View.VISIBLE
        mapStatus.text = "Loading map..."
        mapView.getMapAsync { map ->
            this.map = map
            map.uiSettings.isCompassEnabled = false
            map.addOnCameraMoveListener {
                onMapBearingChanged?.invoke(map.cameraPosition.bearing.toFloat())
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
            }
        }
    }

    private fun requestLocationAccess(focus: Boolean = false) {
        if (hasLocationPermission()) {
            showUserLocation(focus)
            if (focus) focusOnLocationAfterPermission = false
        } else {
            mapStatus.visibility = View.VISIBLE
            mapStatus.text = "Allow location to see your position"
            activity.requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST || !mapStyleLoaded) return

        if (hasLocationPermission()) {
            showUserLocation(focus = focusOnLocationAfterPermission)
            focusOnLocationAfterPermission = false
        } else {
            mapStatus.visibility = View.VISIBLE
            mapStatus.text = "Location access denied"
        }
    }

    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun onMyLocationButtonClicked() {
        focusOnLocationAfterPermission = true
        if (mapStyleLoaded) requestLocationAccess(focus = true)
    }

    @Suppress("MissingPermission")
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
            locationEnabled = true
            val lastLocation = locationComponent.lastKnownLocation
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
            if (lastLocation != null) loadNearbyListings(lastLocation)
            mapStatus.visibility = View.GONE
        } catch (error: Exception) {
            mapStatus.visibility = View.VISIBLE
            mapStatus.text = "Could not show current location"
            mapStatus.contentDescription = error.message
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun loadNearbyListings(location: Location) {
        if (!SupabaseClientProvider.isConfigured) return
        nearbyJob?.cancel()
        nearbyJob = nearbyScope.launch {
            try {
                val listings = nearbyRepository.getNearby(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = DEFAULT_SEARCH_RADIUS_METERS
                )
                val currentMap = map ?: return@launch
                listingMarkers.forEach(currentMap::removeMarker)
                listingMarkers.clear()
                listings.forEach { listing ->
                    val distanceKm = listing.distanceMeters / 1_000.0
                    val details = buildList {
                        if (listing.pickupArea.isNotBlank()) add(listing.pickupArea)
                        add("%.1f km away".format(distanceKm))
                    }.joinToString(" • ")
                    listingMarkers += currentMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(listing.latitude, listing.longitude))
                            .title("${listing.title} · ${listing.price}")
                            .snippet(details)
                    )
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mapStatus.visibility = View.VISIBLE
                mapStatus.text = "Could not load nearby listings"
                mapStatus.contentDescription = error.localizedMessage
            }
        }
    }

    fun setBottomObstructionPx(bottomObstructionPx: Int) {
        val button = myLocationButton ?: return
        val params = button.layoutParams as? FrameLayout.LayoutParams ?: return
        params.bottomMargin = bottomObstructionPx
        button.layoutParams = params
    }

    fun setLabelsVisible(visible: Boolean) {
        labelsVisible = visible
        currentStyle?.layers
            ?.filterIsInstance<SymbolLayer>()
            ?.forEach { layer ->
                layer.setProperties(visibility(if (labelsVisible) Property.VISIBLE else Property.NONE))
            }
    }

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

    fun onStart() = mapView.onStart()
    fun onResume() = mapView.onResume()
    fun onPause() = mapView.onPause()
    fun onStop() = mapView.onStop()
    fun onLowMemory() = mapView.onLowMemory()
    fun onDestroy() {
        nearbyJob?.cancel()
        nearbyScope.cancel()
        mapView.onDestroy()
    }
    fun onSaveInstanceState(outState: Bundle) = mapView.onSaveInstanceState(outState)

    private companion object {
        const val MAP_STYLE_URL = "asset://map/nearnest-map-style.json"
        const val LOCATION_PERMISSION_REQUEST = 4102
        const val USER_LOCATION_ZOOM = 18.0
        const val DEFAULT_SEARCH_RADIUS_METERS = 5_000
    }
}
