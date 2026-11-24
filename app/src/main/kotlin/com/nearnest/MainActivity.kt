package com.nearnest

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.nearnest.data.remote.ApiClientProvider
import com.nearnest.data.remote.ApiException
import com.nearnest.data.remote.ApiUser
import com.nearnest.data.remote.AuthRepository
import com.nearnest.ui.auth.AuthScreen
import com.nearnest.ui.auth.SignupScreen
import com.nearnest.ui.auth.SignupOtpScreen
import com.nearnest.ui.home.HomeScreen
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

        if (!ApiClientProvider.isConfigured) {
            showMessage("Set API_BASE_URL in local.properties, then rebuild the app.")
            return
        }

        showMessage("Connecting to HereBuy API...")
        mainScope.launch(Dispatchers.Main) {
            val authRepository = AuthRepository(this@MainActivity)
            if (!authRepository.hasSession()) {
                showAuthScreen()
                return@launch
            }
            try {
                showHomeScreen(authRepository.fetchCurrentUser())
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) {
                    authRepository.signOut()
                    showAuthScreen("Your session expired. Please sign in again.")
                } else {
                    showMessage("Could not connect to HereBuy API: ${error.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    private fun showAuthScreen(message: String? = null) {
        val authRepository = AuthRepository(this)
        val authScreen = AuthScreen(this, onCreateAccount = { showSignupScreen() }) { identifier, password, onResult ->
            mainScope.launch(Dispatchers.Main) {
                try {
                    showHomeScreen(authRepository.signIn(identifier, password))
                } catch (error: Exception) {
                    onResult(error.localizedMessage ?: "Sign-in failed. Please try again.", true)
                }
            }
        }
        setContentView(authScreen.createView())
        message?.let { authScreen.showMessage(it, isError = true) }
    }

    private fun showSignupScreen() {
        val repository = AuthRepository(this)
        val screen = SignupScreen(
            activity = this,
            onBackToSignIn = { showAuthScreen() },
            onOtpSent = { challengeId, email -> showSignupOtpScreen(challengeId, email) },
            onStartSignup = { username, email, password, onResult ->
                mainScope.launch(Dispatchers.Main) {
                    try {
                        val response = repository.beginSignup(username, email, password)
                        onResult(response, response.message, false)
                    } catch (error: Exception) {
                        onResult(null, error.localizedMessage ?: "Could not start signup. Please try again.", true)
                    }
                }
            }
        )
        setContentView(screen.createView())
    }

    private fun showSignupOtpScreen(challengeId: String, email: String) {
        val repository = AuthRepository(this)
        val screen = SignupOtpScreen(
            activity = this,
            email = email,
            onBack = { showSignupScreen() },
            onVerify = { code, onResult ->
                mainScope.launch(Dispatchers.Main) {
                    try {
                        val user = repository.verifySignup(challengeId, code)
                        showHomeScreen(user)
                        onResult(user, "", false)
                    } catch (error: Exception) {
                        onResult(null, error.localizedMessage ?: "Code verification failed. Please try again.", true)
                    }
                }
            },
            onResend = { onResult ->
                mainScope.launch(Dispatchers.Main) {
                    try {
                        val response = repository.resendSignupCode(challengeId)
                        onResult(response, response.message, false)
                    } catch (error: Exception) {
                        onResult(null, error.localizedMessage ?: "Could not resend the code.", true)
                    }
                }
            }
        )
        setContentView(screen.createView())
    }

    private fun showHomeScreen(currentUser: ApiUser) {
        val screen = HomeScreen(this, currentUser, onSignOut = {
            mainScope.launch(Dispatchers.Main) {
                AuthRepository(this@MainActivity).signOut()
                homeScreen?.let { previous ->
                    if (hasResumed) previous.onPause()
                    if (hasStarted) previous.onStop()
                    previous.onDestroy()
                }
                homeScreen = null
                showAuthScreen()
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

    @Deprecated("Deprecated on Android, retained for runtime permission callback compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        homeScreen?.onRequestPermissionsResult(requestCode, grantResults)
    }
}
