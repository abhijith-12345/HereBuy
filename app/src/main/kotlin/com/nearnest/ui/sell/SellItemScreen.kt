// FILE GUIDE: Collects listing details and posts a new item to the API after validating required fields.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.sell

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.nearnest.data.remote.AuthRepository
import com.nearnest.data.remote.CreateListingRepository
import com.nearnest.ui.map.PickupPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Implements the new listing form and submission state. */

class SellItemScreen(
    private val activity: Activity,
    private val initialPickupPoint: PickupPoint?,
    private val onPickLocation: (PickupPoint?, (PickupPoint) -> Unit) -> Unit,
    private val onClose: () -> Unit,
    private val onListingCreated: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = CreateListingRepository()
    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var pickupAreaInput: EditText
    private lateinit var categoryInput: Spinner
    private lateinit var pickupSummary: TextView
    private lateinit var statusText: TextView
    private lateinit var saveButton: Button
    private var pickupPoint: PickupPoint? = null

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(): View {
        pickupPoint = null
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(241, 247, 241))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(20), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        val back = TextView(activity).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(53, 76, 96))
            contentDescription = "Back"
            setOnClickListener { onClose() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(42), dp(48)))
        header.addView(TextView(activity).apply {
            text = "Sell an item"
            textSize = 20f
            setTextColor(Color.rgb(24, 67, 42))
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        page.addView(header, LinearLayout.LayoutParams(-1, -2))

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        form.addView(sectionTitle("Item details"))
        titleInput = input("What are you selling?", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        form.addView(titleInput, fieldLayout(top = 12))

        categoryInput = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Produce", "Food", "Home", "Clothing", "Other")
            )
            background = fieldBackground()
            setPadding(dp(12), 0, dp(12), 0)
        }
        form.addView(categoryInput, fieldLayout(top = 10))

        priceInput = input("Price (₹)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(priceInput, fieldLayout(top = 10))

        descriptionInput = input(
            "Description (condition, size, details)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 6
            setSingleLine(false)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        form.addView(descriptionInput, LinearLayout.LayoutParams(-1, dp(112)).apply { topMargin = dp(10) })

        form.addView(sectionTitle("Pickup location"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        pickupAreaInput = input("Area or pickup instructions", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        form.addView(pickupAreaInput, fieldLayout(top = 12))
        pickupSummary = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(83, 101, 117))
            setPadding(dp(2), dp(10), dp(2), dp(2))
        }
        updatePickupSummary()
        form.addView(pickupSummary, LinearLayout.LayoutParams(-1, -2))
        val chooseLocation = Button(activity).apply {
            text = "Choose pickup spot on map"
            setTextColor(Color.rgb(24, 130, 91))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(228, 246, 237))
            setOnClickListener {
                onPickLocation(pickupPoint ?: initialPickupPoint) { point ->
                    pickupPoint = point
                    updatePickupSummary()
                }
            }
        }
        form.addView(chooseLocation, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        form.addView(TextView(activity).apply {
            text = "Buyers see this selected spot. Your live location is not shared."
            textSize = 12f
            setTextColor(Color.rgb(108, 122, 135))
            setPadding(dp(2), dp(7), dp(2), 0)
        }, LinearLayout.LayoutParams(-1, -2))

        statusText = TextView(activity).apply {
            textSize = 13f
            visibility = View.GONE
        }
        form.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        saveButton = Button(activity).apply {
            text = "Post item for sale"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(20, 122, 67))
            setOnClickListener { submit() }
        }
        form.addView(saveButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })

        page.addView(ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(form, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    /** Cancels this screen's coroutine scope and releases its pending work. */
    fun onDestroy() = scope.cancel()

    /** Validates form fields and submits the requested API operation. */
    private fun submit() {
        val title = titleInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim()
        val area = pickupAreaInput.text.toString().trim()
        val price = priceInput.text.toString().trim().toDoubleOrNull()
        val point = pickupPoint
        val accessToken = AuthRepository(activity).getAccessToken()
        if (title.isBlank()) return showError("Add an item name.")
        if (title.length > 100) return showError("Item name must be 100 characters or fewer.")
        if (price == null || price < 0.0) return showError("Enter a valid price.")
        if (description.length > 2000) return showError("Description is too long.")
        if (area.length > 120) return showError("Pickup area must be 120 characters or fewer.")
        if (point == null) return showError("Choose the pickup spot on the map.")
        if (accessToken.isNullOrBlank()) return showError("Please sign in again before posting.")

        setLoading(true)
        scope.launch {
            try {
                repository.createListing(
                    accessToken = accessToken,
                    title = title,
                    description = description,
                    category = categoryInput.selectedItem.toString(),
                    price = price,
                    pickupArea = area,
                    latitude = point.latitude,
                    longitude = point.longitude
                )
                Toast.makeText(activity, "Your item is now listed", Toast.LENGTH_SHORT).show()
                onListingCreated()
            } catch (error: Exception) {
                showError(error.localizedMessage ?: "Could not post the item. Please try again.")
                setLoading(false)
            }
        }
    }

    /** Updates controls to show or clear an in-progress request. */
    private fun setLoading(loading: Boolean) {
        saveButton.isEnabled = !loading
        saveButton.text = if (loading) "Posting…" else "Post item for sale"
        statusText.text = if (loading) "Posting your item…" else statusText.text
        statusText.setTextColor(Color.rgb(83, 101, 117))
        statusText.visibility = View.VISIBLE
    }

    /** Displays Error in this component. */
    private fun showError(message: String) {
        statusText.text = message
        statusText.setTextColor(Color.rgb(179, 49, 49))
        statusText.visibility = View.VISIBLE
    }

    /** Refreshes the form summary for the currently selected pickup coordinate. */
    private fun updatePickupSummary() {
        val point = pickupPoint
        pickupSummary.text = if (point == null) {
            "No pickup spot selected yet."
        } else {
            "Pickup spot: %.5f, %.5f".format(point.latitude, point.longitude)
        }
    }

    /** Creates a consistently styled section heading. */
    private fun sectionTitle(text: String) = TextView(activity).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(42, 62, 79))
    }

    /** Creates a consistently styled text-entry field. */
    private fun input(hintText: String, inputTypeValue: Int) = EditText(activity).apply {
        hint = hintText
        textSize = 15f
        setTextColor(Color.rgb(40, 52, 64))
        setHintTextColor(Color.rgb(128, 139, 150))
        inputType = inputTypeValue
        setSingleLine(true)
        setPadding(dp(14), 0, dp(14), 0)
        background = fieldBackground()
    }

    /** Creates the shared rounded field background. */
    private fun fieldBackground() = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.rgb(220, 228, 236))
    }

    /** Creates vertical spacing parameters for a form field. */
    private fun fieldLayout(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply {
        topMargin = dp(top)
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
