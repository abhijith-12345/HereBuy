package com.nearnest.ui.home

import android.app.Activity
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
import com.nearnest.ui.map.MapScreen
import com.nearnest.ui.map.PickupLocationPicker
import com.nearnest.ui.profile.ProfileScreen
import com.nearnest.ui.sell.SellItemScreen

/** Landing screen for HereBuy, with the nearby map as its main content. */
class HomeScreen(
    private val activity: Activity,
    private val currentUser: ApiUser,
    private val onSignOut: () -> Unit = {}
) {
    private val mapScreen = MapScreen(activity)
    private var showMapLabels = true
    private var hasStarted = false
    private var hasResumed = false
    private var activePicker: PickupLocationPicker? = null
    private var activePickerView: View? = null
    private var activeSellScreen: SellItemScreen? = null
    private var activeSellView: View? = null

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
        lateinit var navigationBar: BottomNavBar
        fun dismissPicker() {
            val picker = activePicker ?: return
            if (hasResumed) picker.onPause()
            if (hasStarted) picker.onStop()
            picker.onDestroy()
            activePickerView?.let(root::removeView)
            activePickerView = null
            activePicker = null
        }
        fun closeSellScreen(refreshListings: Boolean = false) {
            dismissPicker()
            activeSellScreen?.onDestroy()
            activeSellScreen = null
            activeSellView?.let(root::removeView)
            activeSellView = null
            profileView.visibility = View.GONE
            mapContent.visibility = View.VISIBLE
            mapControls.forEach { it.visibility = View.VISIBLE }
            navigationBar.visibility = View.VISIBLE
            if (refreshListings) mapScreen.refreshNearbyListings()
        }
        fun showSellScreen() {
            if (activeSellScreen != null) return
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
                onClose = { closeSellScreen() },
                onListingCreated = { closeSellScreen(refreshListings = true) }
            )
            activeSellScreen = screen
            activeSellView = screen.createView()
            root.addView(activeSellView, FrameLayout.LayoutParams(-1, -1))
        }
        navigationBar = BottomNavBar(activity, onDestinationSelected = { destination ->
            when (destination) {
                BottomNavBar.Destination.PROFILE -> {
                    profileScreen.refresh()
                    mapContent.visibility = View.GONE
                    mapControls.forEach { it.visibility = View.GONE }
                    profileView.visibility = View.VISIBLE
                }
                BottomNavBar.Destination.HOME -> {
                    profileView.visibility = View.GONE
                    mapContent.visibility = View.VISIBLE
                    mapControls.forEach { it.visibility = View.VISIBLE }
                }
                BottomNavBar.Destination.CHAT,
                BottomNavBar.Destination.NOTIFICATIONS -> {
                    profileView.visibility = View.GONE
                    mapContent.visibility = View.VISIBLE
                    mapControls.forEach { it.visibility = View.VISIBLE }
                    Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show()
                }
            }
        }, onAddClick = { showSellScreen() })
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
            contentDescription = "Map settings"
            setImageResource(R.drawable.ic_nav_settings)
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
                popup.setOnMenuItemClickListener {
                    showMapLabels = !showMapLabels
                    labelsItem.isChecked = showMapLabels
                    mapScreen.setLabelsVisible(showMapLabels)
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
            activeSellView?.setPadding(0, topInset, 0, bottomInset)

            mapScreen.setBottomObstructionPx(bottomInset + dp(gpsBottomMarginDp))
            insets
        }

        return root
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) =
        mapScreen.onRequestPermissionsResult(requestCode, grantResults)

    fun onStart() {
        hasStarted = true
        mapScreen.onStart()
        activePicker?.onStart()
    }

    fun onResume() {
        hasResumed = true
        mapScreen.onResume()
        activePicker?.onResume()
    }

    fun onPause() {
        hasResumed = false
        activePicker?.onPause()
        mapScreen.onPause()
    }

    fun onStop() {
        hasStarted = false
        activePicker?.onStop()
        mapScreen.onStop()
    }

    fun onLowMemory() {
        mapScreen.onLowMemory()
        activePicker?.onLowMemory()
    }

    fun onDestroy() {
        activePicker?.onDestroy()
        activeSellScreen?.onDestroy()
        mapScreen.onDestroy()
    }

    fun onSaveInstanceState(outState: Bundle) {
        mapScreen.onSaveInstanceState(outState)
        activePicker?.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun isCompactLayout(): Boolean {
        val configuration = activity.resources.configuration
        return configuration.screenHeightDp < 500 ||
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
