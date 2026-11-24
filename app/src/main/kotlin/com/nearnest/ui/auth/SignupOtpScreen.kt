// FILE GUIDE: Builds the email verification form and handles code verification and resend actions.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.auth

import android.app.Activity
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
    private lateinit var resendButton: TextView
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
            text = "Verify your email to finish creating your account.\nCode sent to $email"
            textSize = 15f
            setTextColor(Color.rgb(91, 111, 96))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        codeInput = EditText(activity).apply {
            hint = "6-digit code"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 49, 39))
            setHintTextColor(Color.rgb(124, 145, 128))
            inputType = InputType.TYPE_CLASS_NUMBER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            filters = arrayOf(InputFilter.LengthFilter(6))
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 251, 247))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(210, 226, 211))
            }
        }
        content.addView(codeInput, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(28) })

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(73, 97, 78))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        verifyButton = Button(activity).apply {
            text = "Verify and create account"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 122, 67))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { verify() }
        }
        content.addView(verifyButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })
        resendButton = TextView(activity).apply {
            text = "Resend code"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(20, 122, 67))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Resend verification code"
            setOnClickListener { resend() }
        }
        content.addView(resendButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
            bottomMargin = dp(4)
        })
        content.addView(TextView(activity).apply {
            text = "Back to signup"
            textSize = 15f
            setTextColor(Color.rgb(91, 111, 96))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Back to signup"
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
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
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(73, 97, 78))
        statusText.visibility = View.VISIBLE
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
