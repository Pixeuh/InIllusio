package fr.inillusio

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdsConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val updateFinished: Boolean = false
)

/** UMP is queried at launch; ad requests remain user initiated. */
class AdsConsentManager(context: Context) {
    private val information = UserMessagingPlatform.getConsentInformation(context.applicationContext)
    private val _state = MutableStateFlow(AdsConsentState())
    val state: StateFlow<AdsConsentState> = _state.asStateFlow()

    fun updateForLaunch(activity: Activity) {
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    publishState(updateFinished = true)
                }
            },
            { publishState(updateFinished = true) }
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publishState(updateFinished = true)
            onComplete()
        }
    }

    private fun publishState(updateFinished: Boolean) {
        _state.value = AdsConsentState(
            canRequestAds = information.canRequestAds(),
            privacyOptionsRequired = information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            updateFinished = updateFinished
        )
    }
}
