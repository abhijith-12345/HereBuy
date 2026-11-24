// FILE GUIDE: Builds and validates the account creation form before requesting email verification.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.auth

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.widget.ProgressBar
import android.util.Patterns
import android.view.Gravity
import android.view.View
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
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.rgb(229, 240, 228))
                cornerRadius = 0f
            }
            elevation = 0f
        }
        content.addView(TextView(activity).apply {
            text = "JOIN YOUR LOCAL COMMUNITY"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(Color.rgb(190, 135, 0))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(TextView(activity).apply {
            text = "Create your account"
            textSize = 26f
            setTextColor(Color.rgb(15, 72, 45))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(activity).apply {
            text = "We’ll email you a code to verify your address."
            textSize = 14f
            setTextColor(Color.rgb(91, 111, 96))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        usernameInput = inputField("Full name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        emailInput = inputField("Email address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        passwordInput = inputField("Password (at least 8 characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        confirmPasswordInput = inputField("Confirm password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(usernameInput, fieldLayout(24))
        content.addView(emailInput, fieldLayout(12))
        content.addView(passwordField(passwordInput), fieldLayout(12))
        val strengthLabel = TextView(activity).apply {
            text = "Password strength"
            textSize = 12f
            setTextColor(Color.rgb(91, 111, 96))
            setPadding(dp(2), dp(5), 0, 0)
        }
        val strengthBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 4
            progressTintList = ColorStateList.valueOf(Color.rgb(220, 170, 35))
            progress = 0
        }
        content.addView(strengthLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        content.addView(strengthBar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(5) })
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty()
                val score = listOf(value.length >= 8, value.any(Char::isUpperCase) && value.any(Char::isLowerCase), value.any(Char::isDigit), value.any { !it.isLetterOrDigit() }).count { it }
                strengthBar.progress = score
                strengthLabel.text = when { value.isEmpty() -> "Password strength"; score <= 1 -> "Weak password"; score <= 3 -> "Good password"; else -> "Strong password" }
                strengthLabel.setTextColor(if (score == 4) Color.rgb(20, 122, 67) else if (score <= 1) Color.rgb(185, 68, 54) else Color.rgb(153, 116, 15))
                strengthBar.progressTintList = ColorStateList.valueOf(if (score == 4) Color.rgb(20, 122, 67) else if (score <= 1) Color.rgb(185, 68, 54) else Color.rgb(220, 170, 35))
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
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(20, 122, 67))
            setOnClickListener { submit() }
        }
        content.addView(submitButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
        content.addView(TextView(activity).apply {
            text = "Already have an account?  Sign in"
            textSize = 15f
            setTextColor(Color.rgb(20, 122, 67))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { onBackToSignIn() }
        }, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(229, 240, 228))
            isVerticalScrollBarEnabled = false
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
            }, FrameLayout.LayoutParams(-1, -1))
        }
    }

    /** Validates form fields and submits the requested API operation. */
    private fun submit() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirm = confirmPasswordInput.text.toString()
        if (username.isBlank()) return showMessage("Enter your full name.", true)
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
                val visible = input.inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0
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

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
