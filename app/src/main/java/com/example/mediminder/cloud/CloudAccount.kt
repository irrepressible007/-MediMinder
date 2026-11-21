package com.example.mediminder.cloud

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.mediminder.BuildConfig
import com.example.mediminder.R
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.await
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Who is signed in, and with what. */
data class CloudUser(val uid: String, val name: String?, val email: String?, val provider: String)

/** A sign-in problem, in words for the person. Cancelling is not a problem. */
class SignInException(@StringRes val reason: Int, cause: Throwable? = null) : Exception(cause)

/**
 * Optional sign-in (Google or email) through Firebase Authentication. Signing in only
 * adds the cloud copy of the data; the app works the same without it.
 */
@Singleton
class CloudAccount @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloud: CloudBackup,
) {
    private val _user = MutableStateFlow(current())
    val user: StateFlow<CloudUser?> = _user

    init {
        if (CloudConfig.available) runCatching { FirebaseAuth.getInstance().addAuthStateListener { _user.value = current() } }
    }

    private fun current(): CloudUser? {
        if (!CloudConfig.available) return null
        val u = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() ?: return null
        val provider = u.providerData.map { it.providerId }.lastOrNull { it != "firebase" } ?: "password"
        return CloudUser(u.uid, u.displayName, u.email, provider)
    }

    val signedIn: Boolean get() = _user.value != null

    /**
     * Google: the full "Sign in with Google" account chooser (Credential Manager), which also
     * lets the person add an account. (The quick bottom sheet is meant for automatic prompts.)
     */
    suspend fun signInWithGoogle(activity: Activity) = translate {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.FIREBASE_WEB_CLIENT_ID).build()
        val credential = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            .credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SignInException(R.string.account_error_generic)
        }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    }

    suspend fun signInWithEmail(email: String, password: String) = translate {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createEmailAccount(email: String, password: String) = translate {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun resetPassword(email: String) = translate {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut() {
        if (!CloudConfig.available) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Removes the cloud copy and the sign-in account. This phone's data stays. */
    suspend fun deleteAccount() = translate {
        val user = FirebaseAuth.getInstance().currentUser ?: return@translate
        cloud.deleteAll()
        user.delete().await()
        signOut()
    }

    /** Firebase's errors, as words for the person; anything unexpected goes to the problem log. */
    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (e: SignInException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        throw SignInException(0, e)   // closed the picker: say nothing
    } catch (e: NoCredentialException) {
        // also when Google's service can't be reached in time: keep the real reason
        ErrorLog.record("Account", "Google sign-in found no credential: ${e.type}", e)
        throw SignInException(R.string.account_error_no_google, e)
    } catch (e: FirebaseNetworkException) {
        throw SignInException(R.string.account_error_network, e)
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw SignInException(R.string.account_error_weak_password, e)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw SignInException(R.string.account_error_exists, e)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw SignInException(R.string.account_error_no_account, e)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw SignInException(R.string.account_error_wrong_password, e)
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        throw SignInException(R.string.account_error_sign_in_again, e)
    } catch (e: Exception) {
        ErrorLog.record("Account", "Sign-in or cloud step failed", e)
        throw SignInException(R.string.account_error_generic, e)
    }
}
