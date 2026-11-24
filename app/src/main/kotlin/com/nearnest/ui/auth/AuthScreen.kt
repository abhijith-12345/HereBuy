// FILE GUIDE: Builds the sign-in form and validates credentials before calling the authentication callback.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.ui.auth

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
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
import android.widget.Toast

/** Implements the sign-in screen. */

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
            text = "Sign in to find good things close to home."
            textSize = 15f
            setTextColor(Color.rgb(91, 111, 96))
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
        content.addView(passwordField(passwordInput), fieldLayout(top = 12))

        content.addView(TextView(activity).apply {
            text = "Forgot password?"
            textSize = 15f
            setTextColor(Color.rgb(20, 122, 67))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "Forgot password"
            setOnClickListener {
                Toast.makeText(activity, "Password reset is not available yet.", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
            bottomMargin = dp(2)
        })

        statusText = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.rgb(73, 97, 78))
            visibility = View.GONE
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        signInButton = Button(activity).apply {
            text = "Sign in"
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
        content.addView(signInButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })

        content.addView(TextView(activity).apply {
            text = "────────  or continue with  ────────"
            textSize = 13f
            setTextColor(Color.rgb(120, 137, 123))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
        val socialRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        listOf(
            "Google" to com.nearnest.R.drawable.ic_social_google,
            "Facebook" to com.nearnest.R.drawable.ic_social_facebook,
            "Apple" to com.nearnest.R.drawable.ic_social_apple
        ).forEach { (provider, iconRes) ->
            val chip = FrameLayout(activity).apply {
                clipChildren = false
                clipToPadding = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(dp(1), Color.rgb(218, 226, 220))
                }
                contentDescription = "Continue with $provider"
                isClickable = true
                isFocusable = true
                setOnClickListener { Toast.makeText(activity, "$provider sign-in is not configured yet.", Toast.LENGTH_SHORT).show() }
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    contentDescription = null
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }
            socialRow.addView(chip, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                setMargins(dp(10), dp(6), dp(10), dp(10))
            })
        }
        content.addView(socialRow, LinearLayout.LayoutParams(-1, -2))
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            addView(TextView(activity).apply {
                text = "New to HereBuy?"
                textSize = 15f
                setTextColor(Color.rgb(91, 111, 96))
            })
            addView(TextView(activity).apply {
                text = "Create account"
                textSize = 15f
                setTextColor(Color.rgb(20, 122, 67))
                isClickable = true
                isFocusable = true
                contentDescription = "Create account"
                setPadding(dp(5), 0, 0, 0)
                setOnClickListener { onCreateAccount() }
            })
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

    /** Validates form fields and submits the requested API operation. */
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
    private fun fieldLayout(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply {
        topMargin = dp(top)
    }

    /** Updates controls to show or clear an in-progress request. */
    private fun setLoading(loading: Boolean) {
        signInButton.isEnabled = !loading
        identifierInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        signInButton.text = if (loading) "Signing in..." else "Sign in"
        if (loading) showMessage("Connecting to HereBuy API...", false)
    }

    /** Displays a user-facing status or form validation message. */
    fun showMessage(message: String, isError: Boolean = false) {
        statusText.text = message
        statusText.setTextColor(if (isError) Color.rgb(179, 49, 49) else Color.rgb(73, 97, 78))
        statusText.visibility = View.VISIBLE
    }

    /** Converts a density-independent pixel value to screen pixels. */
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
