package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeliveryRepository
import com.example.model.DeliveryOrder
import com.example.model.DeliveryRating
import com.example.model.KigaliCatalog
import com.example.model.OrderStatus
import com.example.model.OrderStatusEvent
import com.example.model.PaymentMethod
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.service.KigaliRouteOptimizer
import com.example.service.MultiStopRoutePlan
import com.example.service.RouteOptimizationResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class OrderNotificationAlert(
    val id: String,
    val orderId: String,
    val title: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryViewModel(
    private val repository: DeliveryRepository,
    val routeOptimizer: KigaliRouteOptimizer,
    val currentUserId: String,
    private val defaultDisplayName: String
) : ViewModel() {

    private val _selectedRoleOverride = MutableStateFlow<UserRole?>(null)
    val selectedRoleOverride: StateFlow<UserRole?> = _selectedRoleOverride.asStateFlow()

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

    private val _multiStopPlan = MutableStateFlow<MultiStopRoutePlan?>(null)
    val multiStopPlan: StateFlow<MultiStopRoutePlan?> = _multiStopPlan.asStateFlow()

    private val _selectedRoutePreview = MutableStateFlow<RouteOptimizationResult?>(null)
    val selectedRoutePreview: StateFlow<RouteOptimizationResult?> = _selectedRoutePreview.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _inAppAlerts = MutableStateFlow<List<OrderNotificationAlert>>(emptyList())
    val inAppAlerts: StateFlow<List<OrderNotificationAlert>> = _inAppAlerts.asStateFlow()

    private val _latestSystemNotification = MutableStateFlow<OrderNotificationAlert?>(null)
    val latestSystemNotification: StateFlow<OrderNotificationAlert?> = _latestSystemNotification.asStateFlow()

    private val knownOrderStatuses = mutableMapOf<String, String>()
    private var initialSnapshotHydrated = false

    val profileState: StateFlow<UiState<UserProfile?>> = repository
        .observeUserProfile(currentUserId)
        .asUiState("profile")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val barOrdersState: StateFlow<UiState<List<DeliveryOrder>>> = repository
        .observeBarOwnerOrders(currentUserId)
        .asUiState("bar_orders")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val distributorOrdersState: StateFlow<UiState<List<DeliveryOrder>>> = repository
        .observeDistributorAssignedOrders(currentUserId)
        .asUiState("distributor_orders")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val pendingDispatchState: StateFlow<UiState<List<DeliveryOrder>>> = repository
        .observePendingDispatchQueue(currentUserId)
        .asUiState("pending_dispatch")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedOrderEventsState: StateFlow<UiState<List<OrderStatusEvent>>> = _selectedOrderId
        .flatMapLatest { orderId ->
            if (orderId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeOrderEvents(orderId, currentUserId).asUiState("order_events")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val barSubmittedRatingsState: StateFlow<UiState<List<DeliveryRating>>> = repository
        .observeMyRatings(currentUserId, asDistributor = false)
        .asUiState("bar_ratings")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val distributorReceivedRatingsState: StateFlow<UiState<List<DeliveryRating>>> = repository
        .observeMyRatings(currentUserId, asDistributor = true)
        .asUiState("distributor_ratings")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        ensureDefaultProfileIfMissing()
        observeStatusTransitionsForNotifications()
    }

    private fun <T> Flow<T>.asUiState(streamName: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $streamName", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Unable to sync $streamName"))
            }

    private fun ensureDefaultProfileIfMissing() {
        viewModelScope.launch {
            profileState.collect { state ->
                if (state is UiState.Success && state.data == null) {
                    val defaultArea = KigaliCatalog.neighborhoods.first()
                    repository.upsertUserProfile(
                        displayName = defaultDisplayName.ifBlank { "Kigali Operator" },
                        role = UserRole.BAR_OWNER,
                        businessName = "Kimihurura Corner Bar",
                        neighborhood = defaultArea.name,
                        phone = "",
                        lat = defaultArea.lat,
                        lng = defaultArea.lng
                    )
                }
            }
        }
    }

    private fun observeStatusTransitionsForNotifications() {
        viewModelScope.launch {
            barOrdersState.collect { state ->
                if (state is UiState.Success) {
                    processOrderAlerts(state.data)
                }
            }
        }
        viewModelScope.launch {
            pendingDispatchState.collect { state ->
                if (state is UiState.Success) {
                    processPendingDispatchAlerts(state.data)
                }
            }
        }
    }

    private fun processOrderAlerts(orders: List<DeliveryOrder>) {
        if (!initialSnapshotHydrated) {
            orders.forEach { knownOrderStatuses[it.id] = it.status }
            initialSnapshotHydrated = true
            return
        }
        orders.forEach { order ->
            val previous = knownOrderStatuses[order.id]
            if (previous != null && previous != order.status) {
                val alert = OrderNotificationAlert(
                    id = "${order.id}_${order.status}_${System.currentTimeMillis()}",
                    orderId = order.id,
                    title = "${order.barName}: ${order.parsedStatus.label}",
                    message = when (order.parsedStatus) {
                        OrderStatus.ACCEPTED -> "${order.distributorName} accepted your order · ETA ${order.etaMinutes} min"
                        OrderStatus.LOADING -> "${order.distributorName} is loading ${order.totalCrates} crates at ${order.depotName}"
                        OrderStatus.EN_ROUTE -> "En route (${order.routeSummary}) · ETA ${order.etaMinutes} min"
                        OrderStatus.ARRIVED -> "Distributor has arrived! Ready for delivery acceptance & payment."
                        OrderStatus.DELIVERED -> "Delivery signed by ${order.recipientName} & paid (${order.paymentReference})."
                        OrderStatus.CANCELLED -> "Order cancelled."
                        else -> "Status updated to ${order.parsedStatus.label}"
                    }
                )
                pushAlert(alert)
            }
            knownOrderStatuses[order.id] = order.status
        }
    }

    private fun processPendingDispatchAlerts(pending: List<DeliveryOrder>) {
        if (!initialSnapshotHydrated) return
        val currentRole = activeRole()
        if (currentRole != UserRole.DISTRIBUTOR) return
        pending.forEach { order ->
            if (!knownOrderStatuses.containsKey("pending_${order.id}")) {
                knownOrderStatuses["pending_${order.id}"] = order.status
                pushAlert(
                    OrderNotificationAlert(
                        id = "dispatch_${order.id}",
                        orderId = order.id,
                        title = "New Commande Nearby (${order.barNeighborhood})",
                        message = "${order.barName} ordered ${order.totalCrates} crates · ${KigaliCatalog.formatRwf(order.totalAmountRwf)}"
                    )
                )
            }
        }
    }

    private fun pushAlert(alert: OrderNotificationAlert) {
        _inAppAlerts.value = (listOf(alert) + _inAppAlerts.value).take(30)
        _latestSystemNotification.value = alert
    }

    fun consumeSystemNotification() {
        _latestSystemNotification.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun activeRole(): UserRole {
        return _selectedRoleOverride.value
            ?: (profileState.value as? UiState.Success)?.data?.parsedRole
            ?: UserRole.BAR_OWNER
    }

    fun switchRole(role: UserRole) {
        _selectedRoleOverride.value = role
        val profile = (profileState.value as? UiState.Success)?.data
        if (profile != null) {
            viewModelScope.launch {
                repository.upsertUserProfile(
                    displayName = profile.displayName.ifBlank { defaultDisplayName },
                    role = role,
                    businessName = profile.businessName.ifBlank {
                        if (role == UserRole.BAR_OWNER) "Kimihurura Corner Bar" else "Gikondo Bralirwa Depot"
                    },
                    neighborhood = profile.neighborhood.ifBlank { "Kimihurura" },
                    phone = profile.phone,
                    lat = profile.lat,
                    lng = profile.lng
                )
            }
        }
    }

    fun saveProfile(
        displayName: String,
        role: UserRole,
        businessName: String,
        neighborhood: String,
        phone: String
    ) {
        val loc = KigaliCatalog.findNeighborhood(neighborhood)
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.upsertUserProfile(
                displayName = displayName,
                role = role,
                businessName = businessName,
                neighborhood = loc.name,
                phone = phone,
                lat = loc.lat,
                lng = loc.lng
            )
            _isActionLoading.value = false
            result.onSuccess {
                _selectedRoleOverride.value = role
                _snackbarMessage.value = "Business profile saved for ${loc.name}, Kigali"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun selectOrder(orderId: String?) {
        _selectedOrderId.value = orderId
        val order = findOrderById(orderId)
        if (order != null) {
            refreshRouteForOrder(order)
        }
    }

    fun findOrderById(orderId: String?): DeliveryOrder? {
        if (orderId.isNullOrBlank()) return null
        val lists = listOfNotNull(
            (barOrdersState.value as? UiState.Success)?.data,
            (distributorOrdersState.value as? UiState.Success)?.data,
            (pendingDispatchState.value as? UiState.Success)?.data
        )
        return lists.flatten().firstOrNull { it.id == orderId }
    }

    fun refreshRouteForOrder(order: DeliveryOrder) {
        viewModelScope.launch {
            val result = routeOptimizer.computeTrafficAwareRoute(
                originLat = order.driverLat,
                originLng = order.driverLng,
                destLat = order.barLat,
                destLng = order.barLng,
                destNeighborhood = order.barNeighborhood
            )
            _selectedRoutePreview.value = result
        }
    }

    fun optimizeDistributorMultiStopRoute(originLat: Double, originLng: Double) {
        val assigned = (distributorOrdersState.value as? UiState.Success)?.data.orEmpty()
            .filter {
                it.parsedStatus in listOf(
                    OrderStatus.ACCEPTED,
                    OrderStatus.LOADING,
                    OrderStatus.EN_ROUTE,
                    OrderStatus.ARRIVED
                )
            }
        viewModelScope.launch {
            _isActionLoading.value = true
            val plan = routeOptimizer.optimizeMultiStopDeliveries(originLat, originLng, assigned)
            _multiStopPlan.value = plan
            _isActionLoading.value = false
            _snackbarMessage.value = if (plan.orderedStops.isEmpty()) {
                "Accept at least one order to optimize a delivery route"
            } else {
                "Route optimized: ${plan.orderedStops.size} stop(s), ${plan.totalDistanceKm} km · ${plan.totalEtaMinutes} min"
            }
        }
    }

    fun placeCommande(
        barName: String,
        neighborhoodName: String,
        addressNote: String,
        cartQuantities: Map<String, Int>,
        paymentMethod: PaymentMethod,
        deliveryNotes: String,
        onCreated: (String) -> Unit
    ) {
        val selectedItems = KigaliCatalog.products.mapNotNull { product ->
            val qty = cartQuantities[product.sku] ?: 0
            if (qty > 0) product to qty else null
        }
        if (selectedItems.isEmpty()) {
            _snackbarMessage.value = "Select at least 1 Bralirwa crate before placing a commande"
            return
        }
        val totalCrates = selectedItems.sumOf { it.second.toLong() }
        val totalAmountRwf = selectedItems.sumOf { it.first.pricePerCrateRwf * it.second.toLong() }
        val summary = selectedItems.joinToString(", ") { "${it.second}x ${it.first.name}" }
        val area = KigaliCatalog.findNeighborhood(neighborhoodName)
        val nearestDepot = KigaliCatalog.findNearestDepot(area.lat, area.lng)

        viewModelScope.launch {
            _isActionLoading.value = true
            val route = routeOptimizer.computeTrafficAwareRoute(
                originLat = nearestDepot.lat,
                originLng = nearestDepot.lng,
                destLat = area.lat,
                destLng = area.lng,
                destNeighborhood = area.name
            )
            val result = repository.placeOrder(
                barName = barName.ifBlank { "Kigali Bar & Lounge" },
                barNeighborhood = area.name,
                barAddressNote = addressNote,
                barLat = area.lat,
                barLng = area.lng,
                depotName = nearestDepot.name,
                depotLat = nearestDepot.lat,
                depotLng = nearestDepot.lng,
                itemsSummary = summary,
                totalCrates = totalCrates,
                totalAmountRwf = totalAmountRwf,
                paymentMethod = paymentMethod,
                etaMinutes = route.etaMinutes.toLong(),
                distanceKm = route.distanceKm,
                trafficLevel = route.trafficLevel,
                routeSummary = route.routeSummary,
                deliveryNotes = deliveryNotes
            )
            _isActionLoading.value = false
            result.onSuccess { newOrderId ->
                _snackbarMessage.value = "Commande sent to nearby Bralirwa distributors!"
                onCreated(newOrderId)
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to submit commande"
            }
        }
    }

    fun acceptPendingOrder(order: DeliveryOrder, currentLat: Double? = null, currentLng: Double? = null) {
        val profile = (profileState.value as? UiState.Success)?.data
        val distName = profile?.displayName?.ifBlank { defaultDisplayName } ?: defaultDisplayName.ifBlank { "Bralirwa Distributor" }
        val depot = KigaliCatalog.findNearestDepot(order.barLat, order.barLng)
        val startLat = (currentLat ?: profile?.lat ?: depot.lat).coerceIn(-2.5, -1.3)
        val startLng = (currentLng ?: profile?.lng ?: depot.lng).coerceIn(29.0, 31.0)

        viewModelScope.launch {
            _isActionLoading.value = true
            val route = routeOptimizer.computeTrafficAwareRoute(
                originLat = startLat,
                originLng = startLng,
                destLat = order.barLat,
                destLng = order.barLng,
                destNeighborhood = order.barNeighborhood
            )
            val result = repository.acceptOrder(
                order = order,
                distributorName = distName,
                depotName = profile?.businessName?.ifBlank { depot.name } ?: depot.name,
                driverLat = startLat,
                driverLng = startLng,
                etaMinutes = route.etaMinutes.toLong(),
                distanceKm = route.distanceKm,
                trafficLevel = route.trafficLevel,
                routeSummary = route.routeSummary
            )
            _isActionLoading.value = false
            result.onSuccess {
                _selectedRoutePreview.value = route
                _snackbarMessage.value = "Accepted ${order.barName} · Optimized ETA ${route.etaMinutes} min"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not accept order"
            }
        }
    }

    fun advanceOrderStatus(
        order: DeliveryOrder,
        nextStatus: OrderStatus,
        overrideLat: Double? = null,
        overrideLng: Double? = null
    ) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val (newLat, newLng) = when {
                overrideLat != null && overrideLng != null -> overrideLat.coerceIn(-2.5, -1.3) to overrideLng.coerceIn(29.0, 31.0)
                nextStatus == OrderStatus.ARRIVED -> order.barLat to order.barLng
                nextStatus == OrderStatus.EN_ROUTE -> {
                    // Move midpoint closer along corridor if no live GPS override passed
                    ((order.driverLat + order.barLat) / 2.0) to ((order.driverLng + order.barLng) / 2.0)
                }
                else -> order.driverLat to order.driverLng
            }

            val route = routeOptimizer.computeTrafficAwareRoute(
                originLat = newLat,
                originLng = newLng,
                destLat = order.barLat,
                destLng = order.barLng,
                destNeighborhood = order.barNeighborhood
            )
            val eta = if (nextStatus == OrderStatus.ARRIVED) 0L else route.etaMinutes.toLong()
            val dist = if (nextStatus == OrderStatus.ARRIVED) 0.0 else route.distanceKm

            val (title, msg) = when (nextStatus) {
                OrderStatus.LOADING -> "Loading Crates at ${order.depotName}" to "Preparing ${order.totalCrates} Bralirwa crates for dispatch."
                OrderStatus.EN_ROUTE -> "Out for Delivery (${route.trafficLevel.label})" to "${route.routeSummary} · ETA $eta min ($dist km)"
                OrderStatus.ARRIVED -> "Arrived at ${order.barName}" to "Distributor at destination. Ready for payment, signature & photo acceptance."
                else -> "Live GPS Location Updated" to "${route.routeSummary} · ETA $eta min"
            }

            val result = repository.updateTrackingStatus(
                order = order,
                newStatus = nextStatus,
                driverLat = newLat,
                driverLng = newLng,
                etaMinutes = eta,
                distanceKm = dist,
                trafficLevel = route.trafficLevel,
                routeSummary = if (nextStatus == OrderStatus.ARRIVED) "Arrived at ${order.barName}" else route.routeSummary,
                eventTitle = title,
                eventMessage = msg
            )
            _isActionLoading.value = false
            result.onSuccess {
                _selectedRoutePreview.value = route
                _snackbarMessage.value = title
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update delivery status"
            }
        }
    }

    fun completeDeliveryAcceptance(
        order: DeliveryOrder,
        paymentMethod: PaymentMethod,
        paymentReference: String,
        recipientName: String,
        signatureSvgPath: String,
        photoCaption: String,
        photoThumbnailBase64: String,
        deliveryNotes: String,
        onCompleted: () -> Unit
    ) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.completeDeliveryAcceptance(
                order = order,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference,
                recipientName = recipientName,
                signatureSvgPath = signatureSvgPath,
                photoCaption = photoCaption,
                photoThumbnailBase64 = photoThumbnailBase64,
                deliveryNotes = deliveryNotes
            )
            _isActionLoading.value = false
            result.onSuccess {
                _snackbarMessage.value = "Delivery accepted, paid & verified!"
                onCompleted()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to complete delivery acceptance"
            }
        }
    }

    fun cancelOrder(order: DeliveryOrder, reason: String) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.cancelOrder(order, reason)
            _isActionLoading.value = false
            result.onSuccess {
                _snackbarMessage.value = "Commande cancelled"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Unable to cancel order"
            }
        }
    }

    fun submitRating(
        order: DeliveryOrder,
        stars: Int,
        punctualityStars: Int,
        handlingStars: Int,
        comment: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.submitDeliveryRating(
                order = order,
                stars = stars,
                punctualityStars = punctualityStars,
                handlingStars = handlingStars,
                comment = comment
            )
            _isActionLoading.value = false
            result.onSuccess {
                _snackbarMessage.value = "Thank you! Delivery rating submitted."
                onDone()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not submit rating"
            }
        }
    }

    companion object {
        private const val TAG = "DeliveryViewModel"
    }
}
