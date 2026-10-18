package org.nulllauncher.mobile.data.billing

import android.app.Application
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nulllauncher.mobile.NullApp
import org.nulllauncher.mobile.data.settings.SettingsRepository
import java.io.File

/**
 * Заготовка под Google Play Billing.
 * Подписка включает премиум-функции: безлимит сборок, модпаки, тач-редактор, облачные бэкапы.
 * Перед релизом нужно зарегистрировать продукты в Google Play Console и заменить заглушки на реальные ID.
 */
class SubscriptionManager(private val app: Application) : PurchasesUpdatedListener {

    private val ctx: Context = app.applicationContext
    private val settings by lazy { SettingsRepository(ctx) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var billingClient: BillingClient? = null
    private var premiumSkuDetails: SkuDetails? = null

    /** Текущая подписка (загружается из файла; в релизе — валидация на сервере). */
    var subscription: SubscriptionInfo = loadSubscription()
        private set

    init {
        billingClient = BillingClient.newBuilder(ctx)
            .setListener(this)
            .enablePendingPurchases()
            .build()
    }

    fun connect() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                    querySkuDetails()
                }
            }
            override fun onBillingServiceDisconnected() {
                // повторить через некоторое время
            }
        })
    }

    private fun querySkuDetails() {
        // Заглушка: реальные SKU берутся из Google Play Console перед релизом.
        premiumSkuDetails = null
    }

    /** Купить премиум подписку. Результат приходит в колбэк. */
    fun purchasePremium(onResult: (Boolean) -> Unit) {
        val sku = premiumSkuDetails
            ?: run { onResult(false); return }
        val activity = app as? android.app.Activity ?: run { onResult(false); return }
        val flowParams = BillingFlowParams.newBuilder().setSkuDetails(sku).build()
        val result = billingClient?.launchBillingFlow(activity, flowParams)
        if (result?.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
            onResult(false)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK &&
            purchases != null
        ) {
            scope.launch {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {
            acknowledgePurchase(purchase)
            activatePremium(purchase)
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        withContext(Dispatchers.IO) {
            billingClient?.acknowledgePurchase(params) { result ->
                if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                    // логировать неудачу acknowledge
                }
            }
        }
    }

    private fun activatePremium(purchase: Purchase) {
        // Заглушка: срок подписки берётся из purchase.expiryTime; в релизе — валидация на сервере.
        val expiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        val info = SubscriptionInfo(SubscriptionTier.PREMIUM.name, expiresAt)
        subscription = info
        val json = Json.encodeToString(info)
        File(app.filesDir, "subscription.json").writeText(json)
    }

    /** Проверить активность подписки. */
    fun isPremiumActive(): Boolean = subscription.active

    fun end() {
        billingClient?.endConnection()
    }

    private fun loadSubscription(): SubscriptionInfo {
        return try {
            val file = File(app.filesDir, "subscription.json")
            if (file.exists()) Json.decodeFromString(file.readText()) else SubscriptionInfo()
        } catch (_: Exception) {
            SubscriptionInfo()
        }
    }
}
