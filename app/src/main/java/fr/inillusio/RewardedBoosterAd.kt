package fr.inillusio

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/** Loads and shows one user-requested rewarded ad. Awarding is exposed only by onUserEarnedReward. */
class RewardedBoosterAd(private val activity: Activity) {
    private val cancelled = AtomicBoolean(false)

    private fun canShowAd(): Boolean =
        !activity.isFinishing && !activity.isDestroyed &&
            (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    fun cancelPendingLoad() {
        cancelled.set(true)
    }

    fun loadAndShow(unitId: String, onReward: () -> Unit, onFailure: (String) -> Unit) {
        val finished = AtomicBoolean(false)
        fun failOnce(message: String) {
            if (!cancelled.get() && finished.compareAndSet(false, true)) onFailure(message)
        }

        MobileAds.initialize(activity) {
            if (cancelled.get()) return@initialize
            if (!canShowAd()) {
                failOnce("L’application est en arrière-plan. Rouvre-la et réessaie.")
                return@initialize
            }
            RewardedAd.load(activity, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failOnce("Aucune publicité disponible pour le moment. Réessaie plus tard.")
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    if (cancelled.get()) return
                    if (!canShowAd()) {
                        failOnce("L’application est en arrière-plan. Rouvre-la et réessaie.")
                        return
                    }
                    val earned = AtomicBoolean(false)
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (!earned.get()) failOnce("La publicité a été interrompue avant la récompense.")
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            failOnce("Impossible d’afficher la publicité. Réessaie plus tard.")
                        }
                    }
                    ad.show(activity) {
                        if (earned.compareAndSet(false, true) && finished.compareAndSet(false, true)) onReward()
                    }
                }
            })
        }
    }
}
