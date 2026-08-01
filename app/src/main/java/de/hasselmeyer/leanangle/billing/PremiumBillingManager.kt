package de.hasselmeyer.leanangle.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import de.hasselmeyer.leanangle.BuildConfig
import de.hasselmeyer.leanangle.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class PremiumBillingMessage {
    PURCHASE_PENDING,
    PRODUCT_UNAVAILABLE,
    BILLING_ERROR
}

internal data class PremiumEntitlements(
    val isAutomationPackPurchased: Boolean = false,
    val isPremiumSubscribed: Boolean = false
)

internal data class PremiumBillingState(
    // These loading flags describe product/price loading only. Entitlement refresh is separate.
    val isAutomationPackLoading: Boolean = false,
    val isSubscriptionLoading: Boolean = false,
    val isEntitlementRefreshing: Boolean = true,
    val entitlementRefreshTimedOut: Boolean = false,
    val premiumCacheFreshness: PremiumCacheFreshness = PremiumCacheFreshness.MISSING,
    val isAutomationPackPurchased: Boolean = false,
    val isSubscribed: Boolean = false,
    val automationPackPriceLabel: String? = null,
    val subscriptionPriceLabel: String? = null,
    val message: PremiumBillingMessage? = null
)

internal class PremiumBillingManager(
    context: Context,
    private val onEntitlementsChanged: (PremiumEntitlements) -> Unit
) : PurchasesUpdatedListener {
    private val settingsStore = SettingsStore(context.applicationContext)
    private val initialCache = settingsStore.loadBillingEntitlementCache()
    private val initialPremiumDecision = evaluatePremiumCache(
        cache = initialCache,
        nowMs = System.currentTimeMillis()
    )
    private var isAutomationPackPurchased = initialCache.isAutomationPackPurchased
    private var isPremiumSubscribed = initialPremiumDecision.grantsPremium
    private val _state = MutableStateFlow(
        PremiumBillingState(
            premiumCacheFreshness = initialPremiumDecision.freshness,
            isAutomationPackPurchased = isAutomationPackPurchased,
            isSubscribed = isPremiumSubscribed
        )
    )
    val state: StateFlow<PremiumBillingState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isConnecting = false
    private var isClosed = false
    private var pendingProductDetailsLoad = false
    private var refreshGeneration = 0
    private var activeRefreshGeneration: Int? = null
    private var pendingPurchaseQueries = emptySet<String>()

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (isClosed) return
        if (billingClient.isReady) {
            refreshPurchases()
            if (pendingProductDetailsLoad) queryAllProductDetails()
            return
        }
        ensureRefreshPending()
        if (isConnecting) return
        isConnecting = true

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val generation = activeRefreshGeneration ?: beginRefresh()
                    queryPurchasesForRefresh(generation)
                    if (pendingProductDetailsLoad) queryAllProductDetails()
                } else {
                    finishRefreshWithError(activeRefreshGeneration)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                // Auto reconnect is enabled. The five-second refresh timeout makes the UI actionable.
            }
        })
    }

    fun refreshPurchases() {
        if (isClosed) return
        if (activeRefreshGeneration != null) return
        val generation = beginRefresh()
        if (!billingClient.isReady) {
            start()
            return
        }
        queryPurchasesForRefresh(generation)
    }

    fun loadProductDetails() {
        if (isClosed) return
        if (
            _state.value.automationPackPriceLabel != null &&
            _state.value.subscriptionPriceLabel != null
        ) {
            return
        }
        pendingProductDetailsLoad = true
        if (!billingClient.isReady) {
            start()
            return
        }
        queryAllProductDetails()
    }

    fun launchAutomationPackPurchase(activity: Activity) {
        launchPurchase(
            activity = activity,
            productId = BuildConfig.AUTOMATION_PACK_PRODUCT_ID,
            productType = BillingClient.ProductType.INAPP
        )
    }

    fun launchSubscriptionPurchase(activity: Activity) {
        launchPurchase(
            activity = activity,
            productId = BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            productType = BillingClient.ProductType.SUBS
        )
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchaseUpdate(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update {
                    it.copy(
                        isAutomationPackLoading = false,
                        isSubscriptionLoading = false,
                        message = null
                    )
                }
            }
            else -> showProductBillingError()
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        mainHandler.removeCallbacksAndMessages(null)
        billingClient.endConnection()
    }

    private fun ensureRefreshPending() {
        if (activeRefreshGeneration == null) beginRefresh()
    }

    private fun beginRefresh(): Int {
        val generation = ++refreshGeneration
        activeRefreshGeneration = generation
        pendingPurchaseQueries = emptySet()
        _state.update {
            it.copy(
                isEntitlementRefreshing = true,
                entitlementRefreshTimedOut = false,
                message = null
            )
        }
        mainHandler.postDelayed(
            { onRefreshTimeout(generation) },
            ENTITLEMENT_REFRESH_TIMEOUT_MS
        )
        return generation
    }

    private fun onRefreshTimeout(generation: Int) {
        if (activeRefreshGeneration != generation) return
        activeRefreshGeneration = null
        pendingPurchaseQueries = emptySet()
        _state.update {
            it.copy(
                isEntitlementRefreshing = false,
                entitlementRefreshTimedOut = true,
                message = PremiumBillingMessage.BILLING_ERROR
            )
        }
    }

    private fun queryPurchasesForRefresh(generation: Int) {
        if (activeRefreshGeneration != generation) return
        pendingPurchaseQueries = setOf(
            BillingClient.ProductType.INAPP,
            BillingClient.ProductType.SUBS
        )
        queryPurchases(BillingClient.ProductType.INAPP, generation)
        queryPurchases(BillingClient.ProductType.SUBS, generation)
    }

    private fun queryAllProductDetails() {
        if (!billingClient.isReady) return
        pendingProductDetailsLoad = false
        _state.update {
            it.copy(
                isAutomationPackLoading = true,
                isSubscriptionLoading = true
            )
        }
        queryProductDetails(
            productId = BuildConfig.AUTOMATION_PACK_PRODUCT_ID,
            productType = BillingClient.ProductType.INAPP
        ) { details ->
            val price = details.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice
            _state.update {
                it.copy(
                    isAutomationPackLoading = false,
                    automationPackPriceLabel = price,
                    message = null
                )
            }
        }
        queryProductDetails(
            productId = BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID,
            productType = BillingClient.ProductType.SUBS
        ) { details ->
            val price = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.lastOrNull()
                ?.formattedPrice
            _state.update {
                it.copy(
                    isSubscriptionLoading = false,
                    subscriptionPriceLabel = price,
                    message = null
                )
            }
        }
    }

    private fun launchPurchase(activity: Activity, productId: String, productType: String) {
        if (!billingClient.isReady) {
            pendingProductDetailsLoad = true
            start()
            return
        }

        setProductLoading(productType, true)
        queryProductDetails(productId, productType) { productDetails ->
            val offerToken = when (productType) {
                BillingClient.ProductType.INAPP -> productDetails.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.offerToken
                else -> productDetails.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.offerToken
            }
            if (offerToken == null) {
                setProductUnavailable(productType)
                return@queryProductDetails
            }

            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
            val result = billingClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build()
            )
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                showProductBillingError()
            }
        }
    }

    private fun queryProductDetails(
        productId: String,
        productType: String,
        onProductReady: (ProductDetails) -> Unit
    ) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(productType)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            val details = result.productDetailsList.firstOrNull()
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || details == null) {
                setProductUnavailable(productType)
                return@queryProductDetailsAsync
            }
            onProductReady(details)
        }
    }

    private fun queryPurchases(productType: String, generation: Int) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                completePurchaseQuery(generation, productType, hadError = true)
                return@queryPurchasesAsync
            }

            val purchasedProductIds = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap(Purchase::getProducts)
                .toSet()
            val verifiedAtMs = System.currentTimeMillis()
            if (productType == BillingClient.ProductType.INAPP) {
                isAutomationPackPurchased =
                    BuildConfig.AUTOMATION_PACK_PRODUCT_ID in purchasedProductIds
                settingsStore.saveVerifiedAutomationPack(
                    isPurchased = isAutomationPackPurchased,
                    verifiedAtMs = verifiedAtMs
                )
            } else {
                isPremiumSubscribed =
                    BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in purchasedProductIds
                settingsStore.saveVerifiedPremium(
                    isSubscribed = isPremiumSubscribed,
                    verifiedAtMs = verifiedAtMs
                )
            }
            val premiumFreshness = if (productType == BillingClient.ProductType.SUBS) {
                if (isPremiumSubscribed) {
                    PremiumCacheFreshness.FRESH
                } else {
                    PremiumCacheFreshness.MISSING
                }
            } else {
                _state.value.premiumCacheFreshness
            }
            publishEntitlements(premiumFreshness = premiumFreshness)
            acknowledgeCompletedPurchases(purchases)
            updatePendingMessage(purchases)
            completePurchaseQuery(generation, productType, hadError = false)
        }
    }

    private fun completePurchaseQuery(generation: Int, productType: String, hadError: Boolean) {
        if (activeRefreshGeneration != generation) return
        pendingPurchaseQueries = pendingPurchaseQueries - productType
        if (hadError) {
            _state.update { it.copy(message = PremiumBillingMessage.BILLING_ERROR) }
        }
        if (pendingPurchaseQueries.isNotEmpty()) return

        activeRefreshGeneration = null
        _state.update {
            it.copy(
                isEntitlementRefreshing = false,
                entitlementRefreshTimedOut = false
            )
        }
    }

    private fun finishRefreshWithError(generation: Int?) {
        if (generation == null || activeRefreshGeneration != generation) return
        activeRefreshGeneration = null
        pendingPurchaseQueries = emptySet()
        _state.update {
            it.copy(
                isEntitlementRefreshing = false,
                message = PremiumBillingMessage.BILLING_ERROR
            )
        }
    }

    private fun processPurchaseUpdate(purchases: List<Purchase>) {
        val verifiedAtMs = System.currentTimeMillis()
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                if (BuildConfig.AUTOMATION_PACK_PRODUCT_ID in purchase.products) {
                    isAutomationPackPurchased = true
                    settingsStore.saveVerifiedAutomationPack(true, verifiedAtMs)
                }
                if (BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in purchase.products) {
                    isPremiumSubscribed = true
                    settingsStore.saveVerifiedPremium(true, verifiedAtMs)
                }
            }
        publishEntitlements(
            premiumFreshness = if (isPremiumSubscribed) {
                PremiumCacheFreshness.FRESH
            } else {
                _state.value.premiumCacheFreshness
            }
        )
        acknowledgeCompletedPurchases(purchases)
        updatePendingMessage(purchases)
    }

    private fun publishEntitlements(premiumFreshness: PremiumCacheFreshness) {
        _state.update {
            it.copy(
                premiumCacheFreshness = premiumFreshness,
                isAutomationPackPurchased = isAutomationPackPurchased,
                isSubscribed = isPremiumSubscribed
            )
        }
        onEntitlementsChanged(
            PremiumEntitlements(
                isAutomationPackPurchased = isAutomationPackPurchased,
                isPremiumSubscribed = isPremiumSubscribed
            )
        )
    }

    private fun acknowledgeCompletedPurchases(purchases: List<Purchase>) {
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach(::acknowledgePurchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(message = PremiumBillingMessage.BILLING_ERROR) }
            }
        }
    }

    private fun updatePendingMessage(purchases: List<Purchase>) {
        val hasPendingPurchase = purchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING &&
                (BuildConfig.AUTOMATION_PACK_PRODUCT_ID in it.products ||
                    BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in it.products)
        }
        if (!hasPendingPurchase) return
        _state.update { it.copy(message = PremiumBillingMessage.PURCHASE_PENDING) }
    }

    private fun setProductLoading(productType: String, loading: Boolean) {
        _state.update {
            if (productType == BillingClient.ProductType.INAPP) {
                it.copy(isAutomationPackLoading = loading, message = null)
            } else {
                it.copy(isSubscriptionLoading = loading, message = null)
            }
        }
    }

    private fun setProductUnavailable(productType: String) {
        _state.update {
            if (productType == BillingClient.ProductType.INAPP) {
                it.copy(
                    isAutomationPackLoading = false,
                    message = PremiumBillingMessage.PRODUCT_UNAVAILABLE
                )
            } else {
                it.copy(
                    isSubscriptionLoading = false,
                    message = PremiumBillingMessage.PRODUCT_UNAVAILABLE
                )
            }
        }
    }

    private fun showProductBillingError() {
        _state.update {
            it.copy(
                isAutomationPackLoading = false,
                isSubscriptionLoading = false,
                message = PremiumBillingMessage.BILLING_ERROR
            )
        }
    }

    private companion object {
        const val ENTITLEMENT_REFRESH_TIMEOUT_MS = 5_000L
    }
}
