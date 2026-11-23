package com.nearnest

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.nearnest.data.remote.AuthRepository
import com.nearnest.data.remote.SupabaseClientProvider
import com.nearnest.ui.auth.AuthScreen
import com.nearnest.ui.home.HomeScreen
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val mainScope: CoroutineScope = MainScope()
    private var homeScreen: HomeScreen? = null
    private var hasStarted = false
    private var hasResumed = false
    private var pendingSavedState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSavedState = savedInstanceState

        if (!SupabaseClientProvider.isConfigured) {
            showMessage(
                "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties, then rebuild the app."
            )
            return
        }

        if (handleAuthDeepLink(intent)) return

        showMessage("Connecting to HereBuy…")
        mainScope.launch(Dispatchers.Main) {
            try {
                if (AuthRepository().hasSession()) {
                    showHomeScreen()
                } else {
                    showAuthScreen()
                }
            } catch (error: Exception) {
                showMessage("Could not connect to Supabase: ${error.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun showAuthScreen(message: String? = null) {
        val authScreen = AuthScreen(this) { email, password, createAccount, onResult ->
            mainScope.launch(Dispatchers.Main) {
                try {
                    val authRepository = AuthRepository()
                    if (createAccount) {
                        if (authRepository.signUp(email, password)) {
                            showHomeScreen()
                        } else {
                            onResult("Account created. Check your email to confirm, then sign in.", false)
                        }
                    } else {
                        authRepository.signIn(email, password)
                        showHomeScreen()
                    }
                } catch (error: Exception) {
                    onResult(error.localizedMessage ?: "Sign-in failed. Please try again.", true)
                }
            }
        }
        setContentView(authScreen.createView())
        message?.let { authScreen.showMessage(it, isError = true) }
    }

    private fun handleAuthDeepLink(callbackIntent: Intent): Boolean {
        val callbackUri = callbackIntent.data ?: return false
        if (callbackUri.scheme != SupabaseClientProvider.AUTH_REDIRECT_SCHEME ||
            callbackUri.host != SupabaseClientProvider.AUTH_REDIRECT_HOST
        ) {
            return false
        }

        SupabaseClientProvider.client.handleDeeplinks(
            intent = callbackIntent,
            onSessionSuccess = {
                runOnUiThread { showHomeScreen() }
            },
            onError = { error ->
                runOnUiThread { showAuthScreen(error.localizedMessage ?: "Email confirmation failed.") }
            }
        )
        return true
    }

    private fun showHomeScreen() {
        val screen = HomeScreen(this, onSignOut = {
            mainScope.launch(Dispatchers.Main) {
                try {
                    AuthRepository().signOut()
                    homeScreen?.let { previous ->
                        if (hasResumed) previous.onPause()
                        if (hasStarted) previous.onStop()
                        previous.onDestroy()
                    }
                    homeScreen = null
                    showAuthScreen()
                } catch (error: Exception) {
                    showMessage("Could not sign out: ${error.localizedMessage ?: "Unknown error"}")
                }
            }
        })
        homeScreen = screen
        setContentView(screen.createView(pendingSavedState))
        pendingSavedState = null
        if (hasStarted) screen.onStart()
        if (hasResumed) screen.onResume()
    }

    private fun showMessage(message: String) {
        val text = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.rgb(53, 71, 87))
            gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(243, 248, 252))
            addView(text, FrameLayout.LayoutParams(-1, -1))
        })
    }

    override fun onStart() {
        super.onStart()
        hasStarted = true
        homeScreen?.onStart()
    }

    override fun onResume() {
        super.onResume()
        hasResumed = true
        homeScreen?.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    override fun onPause() {
        hasResumed = false
        homeScreen?.onPause()
        super.onPause()
    }

    override fun onStop() {
        hasStarted = false
        homeScreen?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        homeScreen?.onLowMemory()
    }

    override fun onDestroy() {
        homeScreen?.onDestroy()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        homeScreen?.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android, retained for runtime permission callback compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        homeScreen?.onRequestPermissionsResult(requestCode, grantResults)
    }
}
