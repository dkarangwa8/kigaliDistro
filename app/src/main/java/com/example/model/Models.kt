package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    BAR_OWNER("Bar / Shop Owner"),
    DISTRIBUTOR("Bralirwa Distributor");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: BAR_OWNER
    }
}

enum class OrderStatus(val label: String, val stepIndex: Int) {
    PENDING("Awaiting Distributor", 0),
    ACCEPTED("Distributor Assigned", 1),
    LOADING("Loading Crates", 2),
    EN_ROUTE("En Route (Live GPS)", 3),
    ARRIVED("Arrived at Bar", 4),
    DELIVERED("Delivered & Verified", 5),
    CANCELLED("Cancelled", -1);

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class PaymentMethod(val label: String, val ussdCode: String?, val shortBadge: String) {
    MOMO_MTN("MTN MoMo Pay", "*182*8*1#", "MoMo"),
    AIRTEL_MONEY("Airtel Money", "*500#", "Airtel"),
    CARD_POS("Card / Tap POS", null, "Card"),
    CASH_RWF("Cash on Delivery (RWF)", null, "Cash");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: MOMO_MTN
    }
}

enum class PaymentStatus(val label: String) {
    UNPAID("Due on Delivery"),
    PAID("Paid & Settled");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: UNPAID
    }
}

enum class TrafficLevel(val label: String) {
    LIGHT("Clear Traffic"),
    MODERATE("Moderate Traffic"),
    HEAVY("Heavy Congestion");

    companion object {
        fun fromRaw(raw: String?): TrafficLevel =
            entries.firstOrNull { it.name == raw } ?: MODERATE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = UserRole.BAR_OWNER.name,
    val businessName: String = "",
    val neighborhood: String = "Kimihurura",
    val phone: String = "",
    val lat: Double = -1.9536,
    val lng: Double = 30.0928,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class DeliveryOrder(
    @DocumentId val id: String = "",
    val barOwnerId: String = "",
    val barName: String = "",
    val barNeighborhood: String = "",
    val barAddressNote: String = "",
    val barLat: Double = -1.9536,
    val barLng: Double = 30.0928,
    val distributorId: String = "",
    val distributorName: String = "",
    val depotName: String = "",
    val driverLat: Double = -1.9695,
    val driverLng: Double = 30.0748,
    val itemsSummary: String = "",
    val totalCrates: Long = 0L,
    val totalAmountRwf: Long = 0L,
    val status: String = OrderStatus.PENDING.name,
    val paymentMethod: String = PaymentMethod.MOMO_MTN.name,
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val paymentReference: String = "",
    val etaMinutes: Long = 15L,
    val distanceKm: Double = 0.0,
    val trafficLevel: String = TrafficLevel.MODERATE.name,
    val routeSummary: String = "",
    val recipientName: String = "",
    val signatureCaptured: Boolean = false,
    val signatureSvgPath: String = "",
    val photoVerified: Boolean = false,
    val photoCaption: String = "",
    val photoThumbnailBase64: String = "",
    val deliveryNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val deliveredAt: Timestamp? = null
) {
    val parsedStatus: OrderStatus get() = OrderStatus.fromRaw(status)
    val parsedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
    val parsedPaymentStatus: PaymentStatus get() = PaymentStatus.fromRaw(paymentStatus)
    val parsedTrafficLevel: TrafficLevel get() = TrafficLevel.fromRaw(trafficLevel)
}

@Keep
@IgnoreExtraProperties
data class OrderStatusEvent(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val actorId: String = "",
    val participantUids: List<String> = emptyList(),
    val status: String = OrderStatus.PENDING.name,
    val title: String = "",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedStatus: OrderStatus get() = OrderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class DeliveryRating(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val barOwnerId: String = "",
    val barName: String = "",
    val distributorId: String = "",
    val distributorName: String = "",
    val stars: Long = 5L,
    val punctualityStars: Long = 5L,
    val handlingStars: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
