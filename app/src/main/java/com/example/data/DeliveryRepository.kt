package com.example.data

import android.content.Context
import com.example.R
import com.example.model.DeliveryOrder
import com.example.model.DeliveryRating
import com.example.model.OrderStatus
import com.example.model.OrderStatusEvent
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.TrafficLevel
import com.example.model.UserProfile
import com.example.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DeliveryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        role: UserRole,
        businessName: String,
        neighborhood: String,
        phone: String,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanDisplay = displayName.trim().ifBlank { "Kigali User" }.take(80)
            val cleanBusiness = businessName.trim().ifBlank {
                if (role == UserRole.BAR_OWNER) "Kigali Bar & Shop" else "Bralirwa Kigali Depot"
            }.take(100)
            val cleanNeighborhood = neighborhood.trim().ifBlank { "Kimihurura" }.take(60)
            val cleanPhone = phone.trim().take(24)
            val clampedLat = lat.coerceIn(-2.5, -1.3)
            val clampedLng = lng.coerceIn(29.0, 31.0)

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to cleanDisplay,
                    "role" to role.name,
                    "businessName" to cleanBusiness,
                    "neighborhood" to cleanNeighborhood,
                    "lat" to clampedLat,
                    "lng" to clampedLng,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone.isNotEmpty()) {
                    updates["phone"] = cleanPhone
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanDisplay,
                    "role" to role.name,
                    "businessName" to cleanBusiness,
                    "neighborhood" to cleanNeighborhood,
                    "lat" to clampedLat,
                    "lng" to clampedLng,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPhone.isNotEmpty()) {
                    payload["phone"] = cleanPhone
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- ORDERS REAL-TIME OBSERVATION ---

    fun observeBarOwnerOrders(userId: String): Flow<List<DeliveryOrder>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "orders"
        emitAll(
            db.collection("orders")
                .whereEqualTo("barOwnerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DeliveryOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeDistributorAssignedOrders(userId: String): Flow<List<DeliveryOrder>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "orders"
        emitAll(
            db.collection("orders")
                .whereEqualTo("distributorId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DeliveryOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observePendingDispatchQueue(userId: String): Flow<List<DeliveryOrder>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "orders"
        emitAll(
            db.collection("orders")
                .whereEqualTo("status", OrderStatus.PENDING.name)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DeliveryOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeOrderEvents(orderId: String, userId: String): Flow<List<OrderStatusEvent>> = flow {
        require(orderId.isNotBlank() && userId.isNotBlank())
        val path = "orders/$orderId/events"
        emitAll(
            db.collection("orders").document(orderId).collection("events")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OrderStatusEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeMyRatings(userId: String, asDistributor: Boolean): Flow<List<DeliveryRating>> = flow {
        require(userId.isNotBlank())
        val path = "ratings"
        val filterField = if (asDistributor) "distributorId" else "barOwnerId"
        emitAll(
            db.collection("ratings")
                .whereEqualTo(filterField, userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DeliveryRating::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    // --- ORDER MUTATIONS ---

    suspend fun placeOrder(
        barName: String,
        barNeighborhood: String,
        barAddressNote: String,
        barLat: Double,
        barLng: Double,
        depotName: String,
        depotLat: Double,
        depotLng: Double,
        itemsSummary: String,
        totalCrates: Long,
        totalAmountRwf: Long,
        paymentMethod: PaymentMethod,
        etaMinutes: Long,
        distanceKm: Double,
        trafficLevel: TrafficLevel,
        routeSummary: String,
        deliveryNotes: String
    ): Result<String> {
        var docPath = "orders"
        return try {
            val uid = requireUserId()
            require(totalCrates in 1L..500L) { "Order must have between 1 and 500 crates" }
            require(totalAmountRwf in 1000L..50_000_000L) { "Invalid order amount" }
            val docRef = db.collection("orders").document()
            docPath = docRef.path
            val payload = mapOf(
                "barOwnerId" to uid,
                "barName" to barName.trim().ifBlank { "Kigali Bar" }.take(100),
                "barNeighborhood" to barNeighborhood.trim().ifBlank { "Kimihurura" }.take(60),
                "barAddressNote" to barAddressNote.trim().take(200),
                "barLat" to barLat.coerceIn(-2.5, -1.3),
                "barLng" to barLng.coerceIn(29.0, 31.0),
                "distributorId" to "",
                "distributorName" to "",
                "depotName" to depotName.trim().take(100),
                "driverLat" to depotLat.coerceIn(-2.5, -1.3),
                "driverLng" to depotLng.coerceIn(29.0, 31.0),
                "itemsSummary" to itemsSummary.trim().take(600),
                "totalCrates" to totalCrates,
                "totalAmountRwf" to totalAmountRwf,
                "status" to OrderStatus.PENDING.name,
                "paymentMethod" to paymentMethod.name,
                "paymentStatus" to PaymentStatus.UNPAID.name,
                "paymentReference" to "",
                "etaMinutes" to etaMinutes.coerceIn(0L, 240L),
                "distanceKm" to distanceKm.coerceIn(0.0, 100.0),
                "trafficLevel" to trafficLevel.name,
                "routeSummary" to routeSummary.trim().take(200),
                "recipientName" to "",
                "signatureCaptured" to false,
                "signatureSvgPath" to "",
                "photoVerified" to false,
                "photoCaption" to "",
                "photoThumbnailBase64" to "",
                "deliveryNotes" to deliveryNotes.trim().take(400),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            appendOrderEvent(
                orderId = docRef.id,
                participantUids = listOf(uid),
                status = OrderStatus.PENDING,
                title = "Commande Broadcast to Nearby Distributors",
                message = "$totalCrates crates requested for ${barName.trim()} ($barNeighborhood)."
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun acceptOrder(
        order: DeliveryOrder,
        distributorName: String,
        depotName: String,
        driverLat: Double,
        driverLng: Double,
        etaMinutes: Long,
        distanceKm: Double,
        trafficLevel: TrafficLevel,
        routeSummary: String
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            require(order.id.isNotBlank()) { "Order ID missing" }
            val cleanDistName = distributorName.trim().ifBlank { "Bralirwa Distributor" }.take(100)
            db.collection("orders").document(order.id).update(
                mapOf(
                    "distributorId" to uid,
                    "distributorName" to cleanDistName,
                    "depotName" to depotName.trim().take(100),
                    "driverLat" to driverLat.coerceIn(-2.5, -1.3),
                    "driverLng" to driverLng.coerceIn(29.0, 31.0),
                    "status" to OrderStatus.ACCEPTED.name,
                    "etaMinutes" to etaMinutes.coerceIn(0L, 240L),
                    "distanceKm" to distanceKm.coerceIn(0.0, 100.0),
                    "trafficLevel" to trafficLevel.name,
                    "routeSummary" to routeSummary.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val participants = listOf(order.barOwnerId, uid).distinct()
            appendOrderEvent(
                orderId = order.id,
                participantUids = participants,
                status = OrderStatus.ACCEPTED,
                title = "Commande Accepted by $cleanDistName",
                message = "Dispatched from $depotName · ETA $etaMinutes min ($routeSummary)."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTrackingStatus(
        order: DeliveryOrder,
        newStatus: OrderStatus,
        driverLat: Double,
        driverLng: Double,
        etaMinutes: Long,
        distanceKm: Double,
        trafficLevel: TrafficLevel,
        routeSummary: String,
        eventTitle: String,
        eventMessage: String
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            require(order.id.isNotBlank()) { "Order ID missing" }
            db.collection("orders").document(order.id).update(
                mapOf(
                    "status" to newStatus.name,
                    "driverLat" to driverLat.coerceIn(-2.5, -1.3),
                    "driverLng" to driverLng.coerceIn(29.0, 31.0),
                    "etaMinutes" to etaMinutes.coerceIn(0L, 240L),
                    "distanceKm" to distanceKm.coerceIn(0.0, 100.0),
                    "trafficLevel" to trafficLevel.name,
                    "routeSummary" to routeSummary.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val participants = listOf(order.barOwnerId, uid).distinct()
            appendOrderEvent(
                orderId = order.id,
                participantUids = participants,
                status = newStatus,
                title = eventTitle.take(100),
                message = eventMessage.take(300)
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeDeliveryAcceptance(
        order: DeliveryOrder,
        paymentMethod: PaymentMethod,
        paymentReference: String,
        recipientName: String,
        signatureSvgPath: String,
        photoCaption: String,
        photoThumbnailBase64: String,
        deliveryNotes: String
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            val cleanRef = paymentReference.trim().take(80)
            val cleanRecipient = recipientName.trim().take(100)
            val cleanSig = signatureSvgPath.trim().take(15_000)
            require(cleanRef.length >= 4) { "Payment reference must be at least 4 characters" }
            require(cleanRecipient.length >= 2) { "Recipient name must be at least 2 characters" }
            require(cleanSig.length >= 5) { "Digital signature is required" }
            require(photoThumbnailBase64.isNotBlank()) { "Delivery verification photo is required" }

            db.collection("orders").document(order.id).update(
                mapOf(
                    "status" to OrderStatus.DELIVERED.name,
                    "paymentMethod" to paymentMethod.name,
                    "paymentStatus" to PaymentStatus.PAID.name,
                    "paymentReference" to cleanRef,
                    "recipientName" to cleanRecipient,
                    "signatureCaptured" to true,
                    "signatureSvgPath" to cleanSig,
                    "photoVerified" to true,
                    "photoCaption" to photoCaption.trim().take(200),
                    "photoThumbnailBase64" to photoThumbnailBase64.take(85_000),
                    "deliveryNotes" to deliveryNotes.trim().take(400),
                    "deliveredAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val participants = listOf(order.barOwnerId, order.distributorId).filter { it.isNotBlank() }.distinct()
            appendOrderEvent(
                orderId = order.id,
                participantUids = if (participants.isNotEmpty()) participants else listOf(uid),
                status = OrderStatus.DELIVERED,
                title = "Delivery Accepted & Paid (${paymentMethod.shortBadge})",
                message = "Signed by $cleanRecipient · Photo verified · Ref $cleanRef"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(order: DeliveryOrder, reason: String): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            val note = reason.trim().ifBlank { "Cancelled by bar owner" }.take(400)
            db.collection("orders").document(order.id).update(
                mapOf(
                    "status" to OrderStatus.CANCELLED.name,
                    "deliveryNotes" to note,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val participants = listOf(order.barOwnerId, order.distributorId).filter { it.isNotBlank() }.distinct()
            appendOrderEvent(
                orderId = order.id,
                participantUids = if (participants.isNotEmpty()) participants else listOf(uid),
                status = OrderStatus.CANCELLED,
                title = "Commande Cancelled",
                message = note.take(300)
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitDeliveryRating(
        order: DeliveryOrder,
        stars: Int,
        punctualityStars: Int,
        handlingStars: Int,
        comment: String
    ): Result<Unit> {
        val docPath = "ratings/${order.id}"
        return try {
            val uid = requireUserId()
            require(order.id.isNotBlank() && order.distributorId.isNotBlank()) { "Order must have an assigned distributor" }
            val payload = mapOf(
                "orderId" to order.id,
                "barOwnerId" to uid,
                "barName" to order.barName.take(100),
                "distributorId" to order.distributorId,
                "distributorName" to order.distributorName.ifBlank { "Bralirwa Distributor" }.take(100),
                "stars" to stars.coerceIn(1, 5),
                "punctualityStars" to punctualityStars.coerceIn(1, 5),
                "handlingStars" to handlingStars.coerceIn(1, 5),
                "comment" to comment.trim().take(400),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("ratings").document(order.id).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun appendOrderEvent(
        orderId: String,
        participantUids: List<String>,
        status: OrderStatus,
        title: String,
        message: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val eventRef = db.collection("orders").document(orderId).collection("events").document()
        val payload = mapOf(
            "orderId" to orderId,
            "actorId" to uid,
            "participantUids" to participantUids.take(2),
            "status" to status.name,
            "title" to title.take(100),
            "message" to message.take(300),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            eventRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, eventRef.path)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
