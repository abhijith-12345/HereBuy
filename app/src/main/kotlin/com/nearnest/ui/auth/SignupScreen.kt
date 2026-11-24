package com.nearnest.ui.auth

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.data.remote.SignupOtpResponse

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

    fun createView(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(224, 232, 239))
            }
            elevation = dp(4).toFloat()
        }
        content.addView(TextView(activity).apply {
            text = "Create your account"
            textSize = 26f
            setTextColor(Color.rgb(28, 76, 119))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(activity).apply {
            text = "We’ll email you a code to verify your address."
            textSize = 14f
            setTextColor(Color.rgb(95, 111, 126))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        usernameInput = inputField("Username", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        emailInput = inputField("Email address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        passwordInput = inputField("Password (at least 8 characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        confirmPasswordInput = inputField("Confirm password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(usernameInput, fieldLayout(24))
        content.addView(emailInput, fieldLayout(12))
        content.addView(passwordInput, fieldLayout(12))
        content.addView(confirmPasswordInput, fieldLayout(12))

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(74, 95, 112))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        submitButton = Button(activity).apply {
            text = "Continue to email verification"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 119, 210))
            setOnClickListener { submit() }
        }
        content.addView(submitButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
        content.addView(TextView(activity).apply {
            text = "Already have an account?  Sign in"
            textSize = 15f
            setTextColor(Color.rgb(22, 119, 210))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { onBackToSignIn() }
        }, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(243, 248, 252))
            isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER
                marginStart = dp(20)
                marginEnd = dp(20)
                topMargin = dp(24)
                bottomMargin = dp(24)
            })
        }
    }

    private fun submit() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirm = confirmPasswordInput.text.toString()
        if (!username.matches(Regex("^[A-Za-z0-9_.-]{3,32}$"))) {
            return showMessage("Use 3–32 letters, numbers, dots, underscores, or hyphens for your username.", true)
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

    private fun inputField(hintText: String, inputTypeValue: Int) = EditText(activity).apply {
        hint = hintText
        textSize = 16f
        setTextColor(Color.rgb(40, 52, 64))
        setHintTextColor(Color.rgb(130, 142, 153))
        inputType = inputTypeValue
        setSingleLine(true)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            setColor(Color.rgb(248, 250, 252))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(220, 228, 236))
        }
    }

    private fun fieldLayout(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(top) }

    private fun setLoading(loading: Boolean) {
        listOf(usernameInput, emailInput, passwordInput, confirmPasswordInput).forEach { it.isEnabled = !loading }
        submitButton.isEnabled = !loading
        submitButton.text = if (loading) "Sending verification email..." else "Continue to email verification"
    }

    private fun showMessage(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(74, 95, 112))
        statusText.visibility = View.VISIBLE
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
