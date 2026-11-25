// FILE GUIDE: Composes the map, search controls, bottom navigation, profile, posted items, sell form, and pickup picker overlays.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.home

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import com.nearnest.R
import com.nearnest.data.remote.ApiUser
import com.nearnest.data.remote.AuthRepository
import com.nearnest.data.remote.ListingAlertRepository
import com.nearnest.data.remote.PushDeviceRepository
import com.nearnest.ui.map.MapScreen
import com.nearnest.ui.map.PickupLocationPicker
import com.nearnest.ui.profile.ProfileScreen
import com.nearnest.ui.sell.SellItemScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Landing screen for HereBuy, with the nearby map as its main content. */
class HomeScreen(
    private val activity: Activity,
    private val currentUser: ApiUser,
    private val onSignOut: () -> Unit = {}
) {
    private val mapScreen = MapScreen(activity)
    private val alertRepository = ListingAlertRepository(activity)
    private val pushDeviceRepository = PushDeviceRepository()
    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var showMapLabels = true
    private var hasStarted = false
    private var hasResumed = false
    private var activePicker: PickupLocationPicker? = null
    private var activePickerView: View? = null
    private var activeSellScreen: SellItemScreen? = null
    private var activeSellView: View? = null
    private var activeMyListingsScreen: MyListingsScreen? = null
    private var activeMyListingsView: View? = null
    private var notificationsScreen: ListingNotificationsScreen? = null
    private var alertPollingJob: Job? = null
    private var notificationBadgeUpdater: ((Int) -> Unit)? = null
    private var backAction: (() -> Boolean)? = null
    private var pushToken: String? = null
    private var registeredPushSettings: String? = null

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(savedInstanceState: Bundle?): View {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
        }
        val mapContent = mapScreen.createView(savedInstanceState)
        root.addView(mapContent, FrameLayout.LayoutParams(-1, -1))
        val compact = isCompactLayout()
        val navigationBarHeightDp = if (compact) 82 else 100
        val mapControls = mutableListOf<View>()
        val profileScreen = ProfileScreen(activity, currentUser, onSignOut)
        val profileView = profileScreen.createView(navigationBarHeightDp).apply {
            visibility = View.GONE
        }
        val alertsScreen = ListingNotificationsScreen(activity, alertRepository)
        notificationsScreen = alertsScreen
        val alertsView = alertsScreen.createView().apply { visibility = View.GONE }
        lateinit var navigationBar: BottomNavBar
        lateinit var showMyListings: () -> Unit
        var selectedDestination = BottomNavBar.Destination.HOME
        var sellOpenedFromMyListings = false
        /** Closes the pickup map overlay and releases its MapView lifecycle resources. */
        fun dismissPicker() {
            val picker = activePicker ?: return
            if (hasResumed) picker.onPause()
            if (hasStarted) picker.onStop()
            picker.onDestroy()
            activePickerView?.let(root::removeView)
            activePickerView = null
            activePicker = null
        }
        /** Removes the posted-items screen and restores the main map and bottom navigation. */
        fun closeMyListings() {
            activeMyListingsScreen?.onDestroy()
            activeMyListingsScreen = null
            activeMyListingsView?.let(root::removeView)
            activeMyListingsView = null
            profileView.visibility = View.GONE
            alertsView.visibility = View.GONE
            mapContent.visibility = View.VISIBLE
            mapControls.forEach { it.visibility = View.VISIBLE }
            navigationBar.visibility = View.VISIBLE
            selectedDestination = BottomNavBar.Destination.HOME
            navigationBar.selectDestination(BottomNavBar.Destination.HOME)
        }
        /** Removes the sell form and returns to its originating screen, refreshing data when requested. */
        fun closeSellScreen(refreshListings: Boolean = false, returnToMyListings: Boolean = false) {
            dismissPicker()
            activeSellScreen?.onDestroy()
            activeSellScreen = null
            activeSellView?.let(root::removeView)
            activeSellView = null
            profileView.visibility = View.GONE
            alertsView.visibility = View.GONE
            mapContent.visibility = if (returnToMyListings) View.GONE else View.VISIBLE
            mapControls.forEach { it.visibility = if (returnToMyListings) View.GONE else View.VISIBLE }
            activeMyListingsView?.visibility = if (returnToMyListings) View.VISIBLE else View.GONE
            navigationBar.visibility = if (returnToMyListings) View.GONE else View.VISIBLE
            if (!returnToMyListings) {
                selectedDestination = BottomNavBar.Destination.HOME
                navigationBar.selectDestination(BottomNavBar.Destination.HOME)
            }
            if (refreshListings) mapScreen.refreshNearbyListings()
        }
        /** Opens the listing form and remembers whether Back should return to posted items. */
        fun showSellScreen(fromMyListings: Boolean = false) {
            if (activeSellScreen != null) return
            sellOpenedFromMyListings = fromMyListings
            profileView.visibility = View.GONE
            mapContent.visibility = View.GONE
            mapControls.forEach { it.visibility = View.GONE }
            navigationBar.visibility = View.GONE
            val screen = SellItemScreen(
                activity = activity,
                initialPickupPoint = mapScreen.currentMapCenter(),
                onPickLocation = { initialPoint, onSelected ->
                    dismissPicker()
                    val picker = PickupLocationPicker(
                        activity = activity,
                        initialPoint = initialPoint,
                        onSelected = { point ->
                            dismissPicker()
                            onSelected(point)
                        },
                        onCancel = { dismissPicker() }
                    )
                    activePicker = picker
                    activePickerView = picker.createView()
                    root.addView(activePickerView, FrameLayout.LayoutParams(-1, -1))
                    if (hasStarted) picker.onStart()
                    if (hasResumed) picker.onResume()
                },
                onClose = { closeSellScreen(returnToMyListings = fromMyListings) },
                onListingCreated = {
                    closeSellScreen(refreshListings = true, returnToMyListings = fromMyListings)
                    if (fromMyListings) activeMyListingsScreen?.refresh()
                }
            )
            activeSellScreen = screen
            activeSellView = screen.createView()
            root.addView(activeSellView, FrameLayout.LayoutParams(-1, -1))
        }
        // Opens seller-owned listings, reusing and refreshing the screen if it is already open.
        showMyListings = {
            if (activeMyListingsScreen != null) {
                activeMyListingsScreen?.refresh()
            } else {
                profileView.visibility = View.GONE
                alertsView.visibility = View.GONE
                mapContent.visibility = View.GONE
                mapControls.forEach { it.visibility = View.GONE }
                navigationBar.visibility = View.GONE
                val screen = MyListingsScreen(
                    activity = activity,
                    onSellNew = { showSellScreen(fromMyListings = true) },
                    onClose = { closeMyListings() }
                )
                activeMyListingsScreen = screen
                activeMyListingsView = screen.createView()
                root.addView(activeMyListingsView, FrameLayout.LayoutParams(-1, -1))
            }
        }
        navigationBar = BottomNavBar(activity, onDestinationSelected = { destination ->
            selectedDestination = destination
            when (destination) {
                BottomNavBar.Destination.PROFILE -> {
                    alertsView.visibility = View.GONE
                    profileScreen.refresh()
                    mapContent.visibility = View.GONE
                    mapControls.forEach { it.visibility = View.GONE }
                    profileView.visibility = View.VISIBLE
                }
                BottomNavBar.Destination.HOME -> {
                    alertsView.visibility = View.GONE
                    profileView.visibility = View.GONE
                    mapContent.visibility = View.VISIBLE
                    mapControls.forEach { it.visibility = View.VISIBLE }
                }
                BottomNavBar.Destination.NOTIFICATIONS -> {
                    profileView.visibility = View.GONE
                    mapContent.visibility = View.GONE
                    mapControls.forEach { it.visibility = View.GONE }
                    alertsView.visibility = View.VISIBLE
                    alertsScreen.open(mapScreen.currentUserLocation() != null)
                    navigationBar.setNotificationCount(alertRepository.getUnreadCount())
                }
                BottomNavBar.Destination.CHAT -> {
                    alertsView.visibility = View.GONE
                    profileView.visibility = View.GONE
                    mapContent.visibility = View.VISIBLE
                    mapControls.forEach { it.visibility = View.VISIBLE }
                    Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show()
                }
            }
        }, onMyItemsClick = { showMyListings() })
        val searchBarHeightDp = if (compact) 46 else 50
        val searchTopOffsetDp = if (compact) 48 else 80
        val controlSizeDp = if (compact) 42 else 48
        val controlGapDp = if (compact) 8 else 12
        val settingsTopOffsetDp = searchTopOffsetDp + searchBarHeightDp + 12
        val compassBottomMarginDp = navigationBar.preferredHeightDp + controlGapDp
        val gpsBottomMarginDp = compassBottomMarginDp + controlSizeDp + controlGapDp
        val searchWidthDp = (activity.resources.configuration.screenWidthDp - 32)
            .coerceAtLeast(0)
            .coerceAtMost(640)

        val searchBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(220, 228, 236))
            }
            elevation = dp(3).toFloat()
        }
        val searchIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(Color.rgb(91, 111, 130))
            contentDescription = "Search"
        }
        searchBar.addView(searchIcon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val searchInput = EditText(activity).apply {
            hint = "Search nearby items"
            textSize = 15f
            setTextColor(Color.rgb(40, 52, 64))
            setHintTextColor(Color.rgb(128, 139, 150))
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(10), 0, 0, 0)
        }
        searchBar.addView(searchInput, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(
            searchBar,
            FrameLayout.LayoutParams(dp(searchWidthDp), dp(searchBarHeightDp), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(searchTopOffsetDp)
            }
        )
        mapControls += searchBar

        val mapSettingsButton = ImageButton(activity).apply {
            contentDescription = "Map filters"
            setImageResource(R.drawable.ic_map_filter)
            imageTintList = ColorStateList.valueOf(Color.rgb(82, 97, 111))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(220, 228, 236))
            }
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                val popup = PopupMenu(activity, this)
                val labelsItem = popup.menu.add("Show map labels").apply {
                    isCheckable = true
                    isChecked = showMapLabels
                }
                val radiusItem = popup.menu.add("Item alerts within ${alertRepository.getRadiusKm()} km")
                popup.setOnMenuItemClickListener { selected ->
                    if (selected == labelsItem) {
                        showMapLabels = !showMapLabels
                        labelsItem.isChecked = showMapLabels
                        mapScreen.setLabelsVisible(showMapLabels)
                    } else if (selected == radiusItem) {
                        showAlertRadiusDialog { radius ->
                            alertRepository.setRadiusKm(radius)
                            radiusItem.title = "Item alerts within $radius km"
                            notificationsScreen?.refresh(mapScreen.currentUserLocation() != null)
                            restartAlertPolling()
                        }
                    }
                    true
                }
                popup.show()
            }
        }
        root.addView(
            mapSettingsButton,
            FrameLayout.LayoutParams(dp(controlSizeDp), dp(controlSizeDp), Gravity.TOP or Gravity.END).apply {
                marginEnd = dp(16)
                topMargin = dp(settingsTopOffsetDp)
            }
        )
        mapControls += mapSettingsButton

        val compassButton = ImageButton(activity).apply {
            contentDescription = "Reset map direction to north"
            setImageResource(R.drawable.ic_map_compass)
            imageTintList = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(220, 228, 236))
            }
            elevation = dp(4).toFloat()
            val iconPadding = if (compact) 10 else 12
            setPadding(dp(iconPadding), dp(iconPadding), dp(iconPadding), dp(iconPadding))
            setOnClickListener { mapScreen.resetMapOrientation() }
        }
        mapScreen.onMapBearingChanged = { bearing -> compassButton.rotation = -bearing }
        root.addView(
            compassButton,
            FrameLayout.LayoutParams(dp(controlSizeDp), dp(controlSizeDp), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(16)
                bottomMargin = dp(compassBottomMarginDp)
            }
        )
        mapControls += compassButton

        root.addView(profileView, FrameLayout.LayoutParams(-1, -1))
        root.addView(alertsView, FrameLayout.LayoutParams(-1, -1))
        notificationBadgeUpdater = navigationBar::setNotificationCount
        navigationBar.setNotificationCount(alertRepository.getUnreadCount())
        root.addView(
            navigationBar,
            FrameLayout.LayoutParams(-1, dp(navigationBar.preferredHeightDp), Gravity.BOTTOM)
        )
        root.setOnApplyWindowInsetsListener { _, insets ->
            val (topInset, bottomInset) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                systemBars.top to systemBars.bottom
            } else {
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }

            val searchLayout = searchBar.layoutParams as FrameLayout.LayoutParams
            searchLayout.topMargin = topInset + dp(searchTopOffsetDp)
            searchBar.layoutParams = searchLayout

            val settingsLayout = mapSettingsButton.layoutParams as FrameLayout.LayoutParams
            settingsLayout.topMargin = topInset + dp(settingsTopOffsetDp)
            mapSettingsButton.layoutParams = settingsLayout

            val compassLayout = compassButton.layoutParams as FrameLayout.LayoutParams
            compassLayout.bottomMargin = bottomInset + dp(compassBottomMarginDp)
            compassButton.layoutParams = compassLayout

            val navigationLayout = navigationBar.layoutParams as FrameLayout.LayoutParams
            navigationLayout.bottomMargin = bottomInset
            navigationBar.layoutParams = navigationLayout

            profileView.setPadding(0, topInset, 0, bottomInset)
            alertsView.setPadding(0, topInset, 0, bottomInset)
            activeSellView?.setPadding(0, topInset, 0, bottomInset)
            activeMyListingsView?.setPadding(0, topInset, 0, bottomInset)

            mapScreen.setBottomObstructionPx(bottomInset + dp(gpsBottomMarginDp))
            insets
        }

        backAction = {
            when {
                activePicker != null -> {
                    dismissPicker()
                    true
                }
                activeSellScreen != null -> {
                    closeSellScreen(returnToMyListings = sellOpenedFromMyListings)
                    true
                }
                activeMyListingsScreen != null -> {
                    closeMyListings()
                    true
                }
                selectedDestination != BottomNavBar.Destination.HOME -> {
                    navigationBar.selectDestination(BottomNavBar.Destination.HOME)
                    true
                }
                else -> false
            }
        }

        return root
    }

    /** Closes the topmost in-app screen and reports whether Back was consumed. */
    fun handleBackPressed(): Boolean = backAction?.invoke() ?: false

    /** Supplies the current FCM token after Android notification permission is granted. */
    fun setPushToken(token: String) {
        pushToken = token
        registeredPushSettings = null
        restartAlertPolling()
    }

    /** Handles the result of the device location permission prompt. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) =
        mapScreen.onRequestPermissionsResult(requestCode, grantResults).also { restartAlertPolling() }

    /** Forwards Android start lifecycle to owned map views. */
    fun onStart() {
        hasStarted = true
        mapScreen.onStart()
        activePicker?.onStart()
    }

    /** Resumes owned map views and location updates. */
    fun onResume() {
        hasResumed = true
        mapScreen.onResume()
        activePicker?.onResume()
        startAlertPolling()
    }

    /** Pauses owned map views and location updates. */
    fun onPause() {
        hasResumed = false
        alertPollingJob?.cancel()
        alertPollingJob = null
        activePicker?.onPause()
        mapScreen.onPause()
    }

    /** Stops owned map views and location updates. */
    fun onStop() {
        hasStarted = false
        activePicker?.onStop()
        mapScreen.onStop()
    }

    /** Forwards Android low-memory notification to the map view. */
    fun onLowMemory() {
        mapScreen.onLowMemory()
        activePicker?.onLowMemory()
    }

    /** Releases resources and cancels work owned by this screen. */
    fun onDestroy() {
        backAction = null
        alertPollingJob?.cancel()
        alertScope.coroutineContext[Job]?.cancel()
        notificationBadgeUpdater = null
        activePicker?.onDestroy()
        activeSellScreen?.onDestroy()
        activeMyListingsScreen?.onDestroy()
        mapScreen.onDestroy()
    }

    /** Saves state needed to restore the current view. */
    fun onSaveInstanceState(outState: Bundle) {
        mapScreen.onSaveInstanceState(outState)
        activePicker?.onSaveInstanceState(outState)
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    /** Starts foreground polling for newly live listings around the device location. */
    private fun startAlertPolling() {
        if (!hasResumed || alertPollingJob?.isActive == true) return
        val token = AuthRepository(activity).getAccessToken() ?: return
        alertPollingJob = alertScope.launch {
            while (isActive) {
                val location = mapScreen.currentUserLocation()
                if (location != null) {
                    try {
                        pushToken?.let { deviceToken ->
                            val settingsKey = "$deviceToken:${location.latitude}:${location.longitude}:${alertRepository.getRadiusKm()}"
                            if (settingsKey != registeredPushSettings) {
                                pushDeviceRepository.register(
                                    token, deviceToken, location.latitude, location.longitude, alertRepository.getRadiusKm()
                                )
                                registeredPushSettings = settingsKey
                            }
                        }
                        val unread = alertRepository.checkForNewListings(token, location.latitude, location.longitude)
                        notificationBadgeUpdater?.invoke(unread)
                        notificationsScreen?.refresh(true)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.w("HereBuy", "Could not refresh nearby item alerts", error)
                    }
                } else notificationsScreen?.refresh(false)
                delay(ALERT_POLL_INTERVAL_MS)
            }
        }
    }

    /** Restarts foreground alert polling after the radius or location access changes. */
    private fun restartAlertPolling() {
        alertPollingJob?.cancel()
        alertPollingJob = null
        startAlertPolling()
    }

    /** Lets the user choose the distance used to match nearby live listings. */
    private fun showAlertRadiusDialog(onSelected: (Int) -> Unit) {
        val options = intArrayOf(1, 2, 5, 10, 20, 50, 100)
        AlertDialog.Builder(activity)
            .setTitle("Item alert radius")
            .setSingleChoiceItems(options.map { "$it km" }.toTypedArray(), options.indexOf(alertRepository.getRadiusKm())) { dialog, which ->
                onSelected(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Reports whether the device is using a compact or landscape layout. */
    private fun isCompactLayout(): Boolean {
        val configuration = activity.resources.configuration
        return configuration.screenHeightDp < 500 ||
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private companion object {
        const val ALERT_POLL_INTERVAL_MS = 60_000L
    }
}
