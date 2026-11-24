package com.nearnest.ui.auth

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AuthScreen(
    private val activity: Activity,
    private val onCreateAccount: () -> Unit,
    private val onSignIn: (
        identifier: String,
        password: String,
        onResult: (message: String, isError: Boolean) -> Unit
    ) -> Unit
) {
    private lateinit var identifierInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var signInButton: Button
    private lateinit var statusText: TextView

    fun createView(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(224, 232, 239))
            }
            elevation = dp(4).toFloat()
        }

        content.addView(TextView(activity).apply {
            text = "HereBuy"
            textSize = 30f
            setTextColor(Color.rgb(28, 76, 119))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))

        content.addView(TextView(activity).apply {
            text = "Sign in to find good things close to home."
            textSize = 15f
            setTextColor(Color.rgb(95, 111, 126))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        identifierInput = inputField(
            "Phone number or email",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        content.addView(identifierInput, fieldLayout(top = 28))

        passwordInput = inputField(
            "Password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        content.addView(passwordInput, fieldLayout(top = 12))

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(74, 95, 112))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        signInButton = Button(activity).apply {
            text = "Sign in"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 119, 210))
            setOnClickListener { submit() }
        }
        content.addView(signInButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })

        content.addView(TextView(activity).apply {
            text = "New to HereBuy?  Create account"
            textSize = 15f
            setTextColor(Color.rgb(22, 119, 210))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            setOnClickListener { onCreateAccount() }
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
        val identifier = identifierInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val isEmail = identifier.contains('@') && identifier.matches(
            Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        )
        val isPhone = identifier.matches(Regex("^\\+[1-9]\\d{7,14}$"))
        if (!isEmail && !isPhone) {
            return showMessage("Enter a valid email or phone number with country code.", true)
        }
        if (password.length < 8) return showMessage("Password must be at least 8 characters.", true)

        setLoading(true)
        onSignIn(identifier, password) { message, isError ->
            setLoading(false)
            showMessage(message, isError)
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

    private fun fieldLayout(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply {
        topMargin = dp(top)
    }

    private fun setLoading(loading: Boolean) {
        signInButton.isEnabled = !loading
        identifierInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        signInButton.text = if (loading) "Signing in..." else "Sign in"
        if (loading) showMessage("Connecting to HereBuy API...", false)
    }

    fun showMessage(message: String, isError: Boolean = false) {
        statusText.text = message
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(74, 95, 112))
        statusText.visibility = View.VISIBLE
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
