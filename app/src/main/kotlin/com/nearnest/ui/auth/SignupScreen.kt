// FILE GUIDE: Builds and validates the account creation form before requesting email verification.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.auth

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.data.remote.SignupOtpResponse

/** Implements the signup screen. */

class SignupScreen(
    private val activity: Activity,
    private val onBackToSignIn: () -> Unit,
    private val onOtpSent: (challengeId: String, email: String) -> Unit,
    private val onStartSignup: (
        username: String,
        email: String,
        password: String,
        onResult: (SignupOtpResponse?, String, Boolean) -> Unit
    ) -> Unit
) {
    private lateinit var usernameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var statusText: TextView

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 0f
            }
            elevation = 0f
        }

        content.addView(TextView(activity).apply {
            text = "HereBuy"
            textSize = 28f
            setTextColor(Color.rgb(15, 72, 45))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(activity).apply {
            text = "Create your account to find good things close to home."
            textSize = 15f
            setTextColor(Color.rgb(91, 111, 96))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        usernameInput = inputField("Full Name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        emailInput = inputField("Email address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        passwordInput = inputField("Password (at least 8 characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        confirmPasswordInput = inputField("Confirm password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(usernameInput, fieldLayout(28))
        content.addView(emailInput, fieldLayout(12))
        content.addView(passwordField(passwordInput), fieldLayout(12))
        val strengthValue = TextView(activity).apply {
            text = "Not set"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(124, 145, 128))
        }
        val strengthHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(TextView(activity).apply {
                text = "Password strength"
                textSize = 13f
                setTextColor(Color.rgb(91, 111, 96))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(strengthValue, LinearLayout.LayoutParams(-2, -2))
        }
        content.addView(strengthHeader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })

        val strengthSegments = List(4) {
            View(activity).apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(229, 234, 230))
                    cornerRadius = dp(3).toFloat()
                }
            }
        }
        val strengthMeter = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            strengthSegments.forEachIndexed { index, segment ->
                addView(segment, LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                    if (index > 0) marginStart = dp(5)
                })
            }
        }
        content.addView(strengthMeter, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(6) })

        val strengthHint = TextView(activity).apply {
            text = "Use 8+ characters, uppercase and lowercase, a number, and a symbol."
            textSize = 12f
            setTextColor(Color.rgb(124, 145, 128))
            visibility = View.GONE
        }
        content.addView(strengthHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })

        // Strength checks minimum length, mixed case, a number, and a symbol.
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty()
                val showStrength = value.isNotEmpty()
                val feedbackVisibility = if (showStrength) View.VISIBLE else View.GONE
                strengthHeader.visibility = feedbackVisibility
                strengthMeter.visibility = feedbackVisibility
                strengthHint.visibility = feedbackVisibility
                val score = listOf(value.length >= 8, value.any(Char::isUpperCase) && value.any(Char::isLowerCase), value.any(Char::isDigit), value.any { !it.isLetterOrDigit() }).count { it }
                val strengthColor = when {
                    value.isEmpty() -> Color.rgb(124, 145, 128)
                    score <= 1 -> Color.rgb(185, 68, 54)
                    score == 2 -> Color.rgb(220, 154, 35)
                    score == 3 -> Color.rgb(60, 128, 176)
                    else -> Color.rgb(20, 122, 67)
                }
                strengthValue.text = when {
                    value.isEmpty() -> "Not set"
                    score <= 1 -> "Weak"
                    score == 2 -> "Fair"
                    score == 3 -> "Good"
                    else -> "Strong"
                }
                strengthValue.setTextColor(strengthColor)
                strengthSegments.forEachIndexed { index, segment ->
                    val segmentColor = if (index < score) strengthColor else Color.rgb(229, 234, 230)
                    segment.background = GradientDrawable().apply {
                        setColor(segmentColor)
                        cornerRadius = dp(3).toFloat()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(passwordField(confirmPasswordInput), fieldLayout(12))

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(73, 97, 78))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        submitButton = Button(activity).apply {
            text = "Continue to email verification"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 122, 67))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { submit() }
        }
        content.addView(submitButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            addView(TextView(activity).apply {
                text = "Already have an account?"
                textSize = 15f
                setTextColor(Color.rgb(91, 111, 96))
            })
            addView(TextView(activity).apply {
                text = "Sign in"
                textSize = 15f
                setTextColor(Color.rgb(20, 122, 67))
                isClickable = true
                isFocusable = true
                contentDescription = "Sign in"
                setPadding(dp(5), 0, 0, 0)
                setOnClickListener { onBackToSignIn() }
            })
        }, LinearLayout.LayoutParams(-1, -2))

        val scrollView = SignupScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(FrameLayout(activity).apply {
                foregroundGravity = Gravity.CENTER
                addView(content, FrameLayout.LayoutParams(
                    dp((resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 40)
                        .coerceAtMost(dp(480)),
                    -2,
                    Gravity.CENTER
                ).apply {
                    topMargin = dp(24)
                    bottomMargin = dp(24)
                })
            }, FrameLayout.LayoutParams(-1, -2))
        }
        confirmPasswordInput.setOnFocusChangeListener { focusedView, hasFocus ->
            if (hasFocus) scrollView.postDelayed({
                scrollFocusedFieldIntoViewIfCovered(scrollView, focusedView)
            }, FOCUS_SCROLL_DELAY_MS)
        }
        scrollView.setOnApplyWindowInsetsListener { view, insets ->
            val imeBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                0
            }
            val confirmPasswordCovered = imeBottom > 0 &&
                activity.currentFocus === confirmPasswordInput &&
                isFieldCoveredByKeyboard(view as ScrollView, confirmPasswordInput)
            view.setPadding(0, 0, 0, if (confirmPasswordCovered) imeBottom + dp(16) else 0)
            if (confirmPasswordCovered) {
                view.post {
                    scrollFocusedFieldIntoViewIfCovered(view, confirmPasswordInput)
                }
            }
            insets
        }
        return scrollView
    }

    /** Validates form fields and submits the requested API operation. */
    private fun submit() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirm = confirmPasswordInput.text.toString()
        if (username.isBlank()) return showMessage("Enter your full name.", true)
        if (username.any(Char::isWhitespace)) {
            return showMessage("Spaces aren't allowed in the name. Please enter it as one word.", true)
        }
        if (username.length !in 3..32 || !username.matches(Regex("^[A-Za-z0-9_.-]+$"))) {
            return showMessage("Use 3 to 32 characters: letters, numbers, periods, underscores, or hyphens.", true)
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return showMessage("Enter a valid email address.", true)
        if (password.length < 8) return showMessage("Password must be at least 8 characters.", true)
        if (password != confirm) return showMessage("The passwords do not match.", true)

        setLoading(true)
        onStartSignup(username, email, password) { response, message, isError ->
            setLoading(false)
            if (response != null) onOtpSent(response.challengeId, email) else showMessage(message, isError)
        }
    }

    /** Creates a styled form field with the requested keyboard type. */
    private fun inputField(hintText: String, inputTypeValue: Int) = EditText(activity).apply {
        hint = hintText
        textSize = 16f
        setTextColor(Color.rgb(35, 49, 39))
        setHintTextColor(Color.rgb(124, 145, 128))
        inputType = inputTypeValue
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setSingleLine(true)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            setColor(Color.rgb(248, 251, 247))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(210, 226, 211))
        }
    }

    private fun passwordField(input: EditText): FrameLayout = FrameLayout(activity).apply {
        input.setPadding(dp(14), 0, dp(52), 0)
        addView(input, FrameLayout.LayoutParams(-1, -1))
        val eye = ImageButton(activity).apply {
            setImageResource(com.nearnest.R.drawable.ic_password_hidden)
            contentDescription = "Show password"
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                val visible = input.inputType and InputType.TYPE_MASK_VARIATION ==
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                input.inputType = InputType.TYPE_CLASS_TEXT or if (visible) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setImageResource(if (visible) com.nearnest.R.drawable.ic_password_hidden else com.nearnest.R.drawable.ic_password_visible)
                contentDescription = if (visible) "Show password" else "Hide password"
                input.setSelection(input.text.length)
            }
        }
        addView(eye, FrameLayout.LayoutParams(dp(48), -1, Gravity.END or Gravity.CENTER_VERTICAL))
    }

    /** Creates vertical spacing parameters for a form field. */
    private fun fieldLayout(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(top) }

    /** Updates controls to show or clear an in-progress request. */
    private fun setLoading(loading: Boolean) {
        listOf(usernameInput, emailInput, passwordInput, confirmPasswordInput).forEach { it.isEnabled = !loading }
        submitButton.isEnabled = !loading
        submitButton.text = if (loading) "Sending verification email..." else "Continue to email verification"
    }

    /** Displays a user-facing status or form validation message. */
    private fun showMessage(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(73, 97, 78))
        statusText.visibility = View.VISIBLE
    }

    /** Scrolls a focused input only when its bounds overlap the keyboard-covered area. */
    private fun scrollFocusedFieldIntoViewIfCovered(scrollView: ScrollView, focusedView: View) {
        if (isFieldCoveredByKeyboard(scrollView, focusedView)) {
            val rect = Rect(0, 0, focusedView.width, focusedView.height)
            focusedView.requestRectangleOnScreen(rect, true)
        }
    }

    /** Checks whether the keyboard overlaps the bottom of the focused input. */
    private fun isFieldCoveredByKeyboard(scrollView: ScrollView, focusedView: View): Boolean {
        if (!focusedView.isShown || focusedView.width == 0 || focusedView.height == 0) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (scrollView.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0) == 0
        ) return false

        val visibleFrame = Rect()
        scrollView.getWindowVisibleDisplayFrame(visibleFrame)
        val focusedLocation = IntArray(2)
        focusedView.getLocationOnScreen(focusedLocation)
        return focusedLocation[1] + focusedView.height > visibleFrame.bottom
    }

    /** Blocks Android's automatic keyboard scroll for every signup field except confirm password. */
    private inner class SignupScrollView(context: Activity) : ScrollView(context) {
        /** Allows child-driven scrolling only while the covered confirm-password field has focus. */
        override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
            if (!shouldScrollForConfirmPassword()) return false
            return super.requestChildRectangleOnScreen(child, rectangle, immediate)
        }

        /** Prevents automatic viewport-resize scrolling for username, email, and password inputs. */
        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            if (shouldScrollForConfirmPassword()) {
                super.onSizeChanged(width, height, oldWidth, oldHeight)
            }
        }

        /** Reports whether keyboard visibility and focus permit confirm-password scrolling. */
        private fun shouldScrollForConfirmPassword(): Boolean =
            ::confirmPasswordInput.isInitialized &&
                activity.currentFocus === confirmPasswordInput &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    (rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0) > 0)
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val FOCUS_SCROLL_DELAY_MS = 300L
    }
}
