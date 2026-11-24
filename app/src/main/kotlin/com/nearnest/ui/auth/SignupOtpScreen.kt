// FILE GUIDE: Builds the email verification form and handles code verification and resend actions.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.auth

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nearnest.data.remote.ApiUser
import com.nearnest.data.remote.SignupOtpResponse

/** Implements email one-time-code verification. */

class SignupOtpScreen(
    private val activity: Activity,
    private val email: String,
    private val onBack: () -> Unit,
    private val onVerify: (code: String, (ApiUser?, String, Boolean) -> Unit) -> Unit,
    private val onResend: ((SignupOtpResponse?, String, Boolean) -> Unit) -> Unit
) {
    private lateinit var codeInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var resendButton: Button
    private lateinit var statusText: TextView

    /** Builds and returns this component's Android view hierarchy. */
    fun createView(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(224, 232, 239))
            }
            elevation = dp(4).toFloat()
        }
        content.addView(TextView(activity).apply {
            text = "Verify your email"
            textSize = 26f
            setTextColor(Color.rgb(28, 76, 119))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(activity).apply {
            text = "Enter the 6-digit code sent to\n$email"
            textSize = 15f
            setTextColor(Color.rgb(95, 111, 126))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        codeInput = EditText(activity).apply {
            hint = "6-digit code"
            textSize = 22f
            gravity = Gravity.CENTER
            letterSpacing = 0.24f
            setTextColor(Color.rgb(40, 52, 64))
            setHintTextColor(Color.rgb(130, 142, 153))
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 250, 252))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(220, 228, 236))
            }
        }
        content.addView(codeInput, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(26) })

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(74, 95, 112))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        verifyButton = Button(activity).apply {
            text = "Verify and create account"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(22, 119, 210))
            setOnClickListener { verify() }
        }
        content.addView(verifyButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
        resendButton = Button(activity).apply {
            text = "Resend code"
            setTextColor(Color.rgb(22, 119, 210))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(233, 243, 251))
            setOnClickListener { resend() }
        }
        content.addView(resendButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        content.addView(TextView(activity).apply {
            text = "Back to signup"
            textSize = 15f
            setTextColor(Color.rgb(22, 119, 210))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { onBack() }
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

    /** Submits the entered verification code and reports the result. */
    private fun verify() {
        val code = codeInput.text.toString()
        if (!code.matches(Regex("^\\d{6}$"))) return showMessage("Enter the 6-digit code from your email.", true)
        setLoading(true)
        onVerify(code) { user, message, isError ->
            setLoading(false)
            if (user == null) showMessage(message, isError)
        }
    }

    /** Requests a replacement verification code and reports the result. */
    private fun resend() {
        resendButton.isEnabled = false
        onResend { _, message, isError ->
            resendButton.isEnabled = true
            showMessage(message, isError)
        }
    }

    /** Updates controls to show or clear an in-progress request. */
    private fun setLoading(loading: Boolean) {
        codeInput.isEnabled = !loading
        verifyButton.isEnabled = !loading
        resendButton.isEnabled = !loading
        verifyButton.text = if (loading) "Verifying..." else "Verify and create account"
    }

    /** Displays a user-facing status or form validation message. */
    private fun showMessage(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(74, 95, 112))
        statusText.visibility = View.VISIBLE
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
