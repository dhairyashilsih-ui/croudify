package com.crowdpulse.camera.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

class AuthManager(private val context: Context) {

    private val signInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent() = signInClient.signInIntent

    fun handleSignInResult(completedTask: Task<GoogleSignInAccount>): String? {
        return try {
            val account = completedTask.result
            val email = account.email
            val token = account.idToken
            Log.d("AuthManager", "Signed in User: $email, Token: $token")
            email
        } catch (e: Exception) {
            Log.e("AuthManager", "Google sign in failed", e)
            null
        }
    }

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
