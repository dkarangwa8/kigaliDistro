package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DeliveryOrder
import com.example.model.DeliveryRating
import com.example.model.KigaliCatalog
import com.example.model.OrderStatus
import com.example.model.OrderStatusEvent
import com.example.service.ProofMediaUtils
import com.example.ui.components.DeliveryProgressStepper
import com.example.ui.components.SignaturePreviewCanvas
import com.example.ui.components.StarRatingRow
import com.example.ui.components.StatusPill
import com.example.ui.components.TrafficPill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.BrewGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PaidVerifiedBlue
import com.example.ui.theme.TrafficLightGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: DeliveryOrder?,
    currentUserId: String,
    events: List<OrderStatusEvent>,
    existingRating: DeliveryRating?,
    isActionLoading: Boolean,
    onBack: () -> Unit,
    onAcceptOrder: (DeliveryOrder) -> Unit,
    onAdvanceStatus: (DeliveryOrder, OrderStatus) -> Unit,
    onOpenAcceptance: (String) -> Unit,
    onCancelOrder: (DeliveryOrder, String) -> Unit,
    onSubmitRating: (DeliveryOrder, Int, Int, Int, String) -> Unit
) {
    var showRatingDialog by remember { mutableStateOf(false) }

    if (order == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Order Tracking") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Order details unavailable.")
            }
        }
        return
    }

    val isDistributor = order.distributorId == currentUserId
    val isBarOwner = order.barOwnerId == currentUserId
    val status = order.parsedStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order.barName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("order_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Status & Stepper Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(status = status)
                            if (status != OrderStatus.DELIVERED && status != OrderStatus.CANCELLED) {
                                TrafficPill(level = order.parsedTrafficLevel, etaMinutes = order.etaMinutes)
                            }
                        }

                        if (status != OrderStatus.CANCELLED) {
                            DeliveryProgressStepper(currentStatus = status)
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Commande Breakdown", style = MaterialTheme.typography.labelLarge)
                            Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${order.totalCrates} crate(s) · ${order.barNeighborhood}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = KigaliCatalog.formatRwf(order.totalAmountRwf),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (order.routeSummary.isNotBlank()) {
                                Text(
                                    text = "Route: ${order.routeSummary} (${order.distanceKm} km)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (order.distributorName.isNotBlank()) {
                                Text(
                                    text = "Distributor: ${order.distributorName} (${order.depotName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Dual-Party Action Panel
            if (status != OrderStatus.DELIVERED && status != OrderStatus.CANCELLED) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Delivery Controls", style = MaterialTheme.typography.titleMedium)

                            if (status == OrderStatus.PENDING) {
                                Button(
                                    onClick = { onAcceptOrder(order) },
                                    enabled = !isActionLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("detail_accept_order_button")
                                ) {
                                    Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Accept Commande as Distributor")
                                }
                            }

                            if (isDistributor) {
                                when (status) {
                                    OrderStatus.ACCEPTED -> {
                                        Button(
                                            onClick = { onAdvanceStatus(order, OrderStatus.LOADING) },
                                            enabled = !isActionLoading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("status_loading_button")
                                        ) {
                                            Text("Start Loading Crates at Depot")
                                        }
                                    }
                                    OrderStatus.LOADING -> {
                                        Button(
                                            onClick = { onAdvanceStatus(order, OrderStatus.EN_ROUTE) },
                                            enabled = !isActionLoading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("status_enroute_button")
                                        ) {
                                            Text("Depart En Route (Live GPS)")
                                        }
                                    }
                                    OrderStatus.EN_ROUTE -> {
                                        Button(
                                            onClick = { onAdvanceStatus(order, OrderStatus.ARRIVED) },
                                            enabled = !isActionLoading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("status_arrived_button")
                                        ) {
                                            Text("Mark Arrived at ${order.barName}")
                                        }
                                    }
                                    else -> Unit
                                }
                            }

                            if ((isDistributor || isBarOwner) && (status == OrderStatus.EN_ROUTE || status == OrderStatus.ARRIVED)) {
                                FilledTonalButton(
                                    onClick = { onOpenAcceptance(order.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("detail_open_acceptance_button")
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Delivery Acceptance (Pay, Sign & Photo)")
                                }
                            }

                            if (isBarOwner && (status == OrderStatus.PENDING || status == OrderStatus.ACCEPTED)) {
                                OutlinedButton(
                                    onClick = { onCancelOrder(order, "Cancelled by bar owner prior to transit") },
                                    enabled = !isActionLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("cancel_order_button")
                                ) {
                                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel Commande")
                                }
                            }
                        }
                    }
                }
            }

            // Proof of Delivery & Digital Payment Receipt (when DELIVERED)
            if (status == OrderStatus.DELIVERED) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.border(1.5.dp, TrafficLightGreen, MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = TrafficLightGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("Verified Proof of Delivery & Receipt", style = MaterialTheme.typography.titleMedium)
                            }

                            Surface(
                                color = PaidVerifiedBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = PaidVerifiedBlue, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${order.parsedPaymentMethod.label} · PAID",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = PaidVerifiedBlue
                                            )
                                            Text(
                                                text = "Ref: ${order.paymentReference}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                            )
                                        }
                                    }
                                    Text(
                                        text = KigaliCatalog.formatRwf(order.totalAmountRwf),
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = PaidVerifiedBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Accepted by: ${order.recipientName} · ${formatTimestamp(order.deliveredAt ?: order.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (order.signatureSvgPath.isNotBlank()) {
                                Text("Captured Digital Signature", style = MaterialTheme.typography.labelMedium)
                                SignaturePreviewCanvas(serializedStrokes = order.signatureSvgPath)
                            }

                            val photoBitmap = remember(order.photoThumbnailBase64) {
                                ProofMediaUtils.decodeBase64ToImageBitmap(order.photoThumbnailBase64)
                            }
                            if (photoBitmap != null) {
                                Text("Verification Photo", style = MaterialTheme.typography.labelMedium)
                                Image(
                                    bitmap = photoBitmap,
                                    contentDescription = "Verified delivery photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                if (order.photoCaption.isNotBlank()) {
                                    Text(
                                        text = order.photoCaption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Post-Delivery Personnel Rating Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = BrewGold)
                                Spacer(Modifier.width(8.dp))
                                Text("Delivery Personnel Rating", style = MaterialTheme.typography.titleMedium)
                            }
                            if (existingRating != null) {
                                StarRatingRow(rating = existingRating.stars.toInt(), starSizeDp = 24)
                                Text(
                                    text = "Punctuality: ${existingRating.punctualityStars}/5 · Crate Handling: ${existingRating.handlingStars}/5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (existingRating.comment.isNotBlank()) {
                                    Text(
                                        text = "\"${existingRating.comment}\"",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else if (isBarOwner) {
                                Text(
                                    text = "How was ${order.distributorName}'s arrival time and crate handling?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { showRatingDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("rate_distributor_button")
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Rate Delivery Personnel")
                                }
                            } else {
                                Text(
                                    text = "Awaiting rating from ${order.barName}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Status Update Timeline
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Live Status Updates (${events.size})", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (events.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "Status updates and delivery notifications appear here in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(events, key = { it.id }) { event ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    text = formatTimestamp(event.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showRatingDialog) {
        RateDistributorDialog(
            distributorName = order.distributorName.ifBlank { "Bralirwa Distributor" },
            onDismiss = { showRatingDialog = false },
            onSubmit = { overall, punctuality, handling, comment ->
                onSubmitRating(order, overall, punctuality, handling, comment)
                showRatingDialog = false
            }
        )
    }
}

@Composable
private fun RateDistributorDialog(
    distributorName: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, Int, Int, String) -> Unit
) {
    var overall by remember { mutableIntStateOf(5) }
    var punctuality by remember { mutableIntStateOf(5) }
    var handling by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate $distributorName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Overall Experience", style = MaterialTheme.typography.labelLarge)
                StarRatingRow(rating = overall, onRatingChange = { overall = it })

                Text("Timely Arrival (Traffic Routing)", style = MaterialTheme.typography.labelLarge)
                StarRatingRow(rating = punctuality, onRatingChange = { punctuality = it })

                Text("Crate Condition & Professionalism", style = MaterialTheme.typography.labelLarge)
                StarRatingRow(rating = handling, onRatingChange = { handling = it })

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(400) },
                    label = { Text("Review Comment (optional)") },
                    placeholder = { Text("Fast arrival, cold crates intact!") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(overall, punctuality, handling, comment) },
                modifier = Modifier.testTag("submit_rating_confirm_button")
            ) {
                Text("Submit Rating")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
