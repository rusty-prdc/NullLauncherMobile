package org.nulllauncher.mobile.data.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Уровни подписки nullLauncher Mobile. */
enum class SubscriptionTier {
    FREE,
    PREMIUM;
}

/** Продукты в Google Play Console (зарегистрировать ID перед релизом). */
object BillingProducts {
    const val PREMIUM_MONTHLY = "nulllauncher_premium_monthly"
    const val PREMIUM_YEARLY = "nulllauncher_premium_yearly"
}

/** Текущая подписка. Активна, если tier == PREMIUM и срок не истёк. */
@Serializable
data class SubscriptionInfo(
    @SerialName("tier") val tier: String = SubscriptionTier.FREE.name,
    @SerialName("expires_at") val expiresAt: Long = 0,
) {
    val active: Boolean get() = tier == SubscriptionTier.PREMIUM.name && expiresAt > System.currentTimeMillis()
}
