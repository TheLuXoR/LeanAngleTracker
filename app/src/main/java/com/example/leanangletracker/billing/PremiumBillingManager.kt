package com.example.leanangletracker.billing

import android.app.Activity
import android.content.Context
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
import com.example.leanangletracker.BuildConfig
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
    val isAutoResumePurchased: Boolean = false,
    val isPremiumSubscribed: Boolean = false
)

internal data class PremiumBillingState(
    val isAutoResumeLoading: Boolean = true,
    val isSubscriptionLoading: Boolean = true,
    val isAutoResumePurchased: Boolean = false,
    val isSubscribed: Boolean = false,
    val autoResumePriceLabel: String? = null,
    val subscriptionPriceLabel: String? = null,
    val message: PremiumBillingMessage? = null
)

internal class PremiumBillingManager(
    context: Context,
    private val onEntitlementsChanged: (PremiumEntitlements) -> Unit
) : PurchasesUpdatedListener {
    private val _state = MutableStateFlow(PremiumBillingState())
    val state: StateFlow<PremiumBillingState> = _state.asStateFlow()
    private var isConnecting = false
    private var isAutoResumePurchased = false
    private var isPremiumSubscribed = false
    private var isInAppQueryComplete = false
    private var isSubscriptionQueryComplete = false

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
        if (billingClient.isReady) {
            refreshPurchases()
            queryAllProductDetails()
            return
        }
        if (isConnecting) return
        isConnecting = true

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchases()
                    queryAllProductDetails()
                } else {
                    showBillingError()
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
            }
        })
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            start()
            return
        }
        isInAppQueryComplete = false
        isSubscriptionQueryComplete = false
        queryPurchases(BillingClient.ProductType.INAPP)
        queryPurchases(BillingClient.ProductType.SUBS)
    }

    fun launchAutoResumePurchase(activity: Activity) {
        launchPurchase(
            activity = activity,
            productId = BuildConfig.AUTO_RESUME_PRODUCT_ID,
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
                        isAutoResumeLoading = false,
                        isSubscriptionLoading = false,
                        message = null
                    )
                }
            }
            else -> showBillingError()
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun queryAllProductDetails() {
        queryProductDetails(
            productId = BuildConfig.AUTO_RESUME_PRODUCT_ID,
            productType = BillingClient.ProductType.INAPP
        ) { details ->
            val price = details.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice
            _state.update {
                it.copy(isAutoResumeLoading = false, autoResumePriceLabel = price, message = null)
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
                it.copy(isSubscriptionLoading = false, subscriptionPriceLabel = price, message = null)
            }
        }
    }

    private fun launchPurchase(activity: Activity, productId: String, productType: String) {
        if (!billingClient.isReady) {
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
                showBillingError()
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

    private fun queryPurchases(productType: String) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                showBillingError()
                return@queryPurchasesAsync
            }

            val purchasedProductIds = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap(Purchase::getProducts)
                .toSet()
            if (productType == BillingClient.ProductType.INAPP) {
                isAutoResumePurchased = BuildConfig.AUTO_RESUME_PRODUCT_ID in purchasedProductIds
                isInAppQueryComplete = true
            } else {
                isPremiumSubscribed = BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in purchasedProductIds
                isSubscriptionQueryComplete = true
            }
            if (isInAppQueryComplete && isSubscriptionQueryComplete) {
                updateEntitlements()
            }
            acknowledgeCompletedPurchases(purchases)
            updatePendingMessage(purchases)
        }
    }

    private fun processPurchaseUpdate(purchases: List<Purchase>) {
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                if (BuildConfig.AUTO_RESUME_PRODUCT_ID in purchase.products) {
                    isAutoResumePurchased = true
                }
                if (BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in purchase.products) {
                    isPremiumSubscribed = true
                }
            }
        updateEntitlements()
        acknowledgeCompletedPurchases(purchases)
        updatePendingMessage(purchases)
    }

    private fun updateEntitlements() {
        _state.update {
            it.copy(
                isAutoResumeLoading = false,
                isSubscriptionLoading = false,
                isAutoResumePurchased = isAutoResumePurchased,
                isSubscribed = isPremiumSubscribed
            )
        }
        onEntitlementsChanged(
            PremiumEntitlements(
                isAutoResumePurchased = isAutoResumePurchased,
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
                showBillingError()
            }
        }
    }

    private fun updatePendingMessage(purchases: List<Purchase>) {
        val hasPendingPurchase = purchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING &&
                (BuildConfig.AUTO_RESUME_PRODUCT_ID in it.products ||
                    BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID in it.products)
        }
        _state.update {
            it.copy(
                message = if (hasPendingPurchase) PremiumBillingMessage.PURCHASE_PENDING else null
            )
        }
    }

    private fun setProductLoading(productType: String, loading: Boolean) {
        _state.update {
            if (productType == BillingClient.ProductType.INAPP) {
                it.copy(isAutoResumeLoading = loading, message = null)
            } else {
                it.copy(isSubscriptionLoading = loading, message = null)
            }
        }
    }

    private fun setProductUnavailable(productType: String) {
        _state.update {
            if (productType == BillingClient.ProductType.INAPP) {
                it.copy(
                    isAutoResumeLoading = false,
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

    private fun showBillingError() {
        _state.update {
            it.copy(
                isAutoResumeLoading = false,
                isSubscriptionLoading = false,
                message = PremiumBillingMessage.BILLING_ERROR
            )
        }
    }
}
