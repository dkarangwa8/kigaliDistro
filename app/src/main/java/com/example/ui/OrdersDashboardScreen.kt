package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DeliveryOrder
import com.example.model.KigaliCatalog
import com.example.model.OrderStatus
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.OrderSummaryCard
import com.example.ui.theme.BrewAmberBright
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KigaliEmerald
import com.example.ui.theme.KigaliEmeraldDeep
import com.example.viewmodel.UiState

@Composable
fun OrdersDashboardScreen(
    activeRole: UserRole,
    profile: UserProfile?,
    barOrdersState: UiState<List<DeliveryOrder>>,
    distributorOrdersState: UiState<List<DeliveryOrder>>,
    pendingDispatchState: UiState<List<DeliveryOrder>>,
    isActionLoading: Boolean,
    onCreateOrderClick: () -> Unit,
    onOrderClick: (String) -> Unit,
    onAcceptOrder: (DeliveryOrder) -> Unit,
    onOpenAcceptance: (String) -> Unit,
    onOpenMapRoute: () -> Unit
) {
    val barOrders = (barOrdersState as? UiState.Success)?.data.orEmpty()
    val distOrders = (distributorOrdersState as? UiState.Success)?.data.orEmpty()
    val pendingQueue = (pendingDispatchState as? UiState.Success)?.data.orEmpty()

    val userLat = profile?.lat ?: -1.9536
    val userLng = profile?.lng ?: 30.0928

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Role-Adaptive Hero Operations Banner
        item {
            OperationsHeroCard(
                activeRole = activeRole,
                profile = profile,
                primaryMetricLabel = if (activeRole == UserRole.BAR_OWNER) "Active Commandes" else "Nearby Pending",
                primaryMetricValue = if (activeRole == UserRole.BAR_OWNER) {
                    barOrders.count { it.parsedStatus != OrderStatus.DELIVERED && it.parsedStatus != OrderStatus.CANCELLED }.toString()
                } else {
                    pendingQueue.size.toString()
                },
                secondaryMetricLabel = if (activeRole == UserRole.BAR_OWNER) "Completed" else "My Active Stops",
                secondaryMetricValue = if (activeRole == UserRole.BAR_OWNER) {
                    barOrders.count { it.parsedStatus == OrderStatus.DELIVERED }.toString()
                } else {
                    distOrders.count { it.parsedStatus != OrderStatus.DELIVERED && it.parsedStatus != OrderStatus.CANCELLED }.toString()
                },
                onPrimaryAction = if (activeRole == UserRole.BAR_OWNER) onCreateOrderClick else onOpenMapRoute
            )
        }

        // Error state banner if any Firestore stream failed
        val streamError = (barOrdersState as? UiState.Error)?.message
            ?: (distributorOrdersState as? UiState.Error)?.message
            ?: (pendingDispatchState as? UiState.Error)?.message
        if (streamError != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Sync notice: $streamError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (activeRole == UserRole.BAR_OWNER) {
            item {
                SectionHeaderRow(
                    title = "Live Beverage Commandes",
                    subtitle = "Tap any order for GPS map & delivery acceptance"
                )
            }

            if (barOrdersState is UiState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (barOrders.isEmpty()) {
                item {
                    EmptyOrdersIllustrationCard(
                        title = "No Bralirwa commandes yet",
                        subtitle = "Place your first crate commande and nearby Kigali distributors will receive it instantly.",
                        ctaLabel = "Place First Commande",
                        onCtaClick = onCreateOrderClick
                    )
                }
            } else {
                items(barOrders, key = { it.id }) { order ->
                    OrderSummaryCard(
                        order = order,
                        modifier = Modifier.testTag("bar_order_card_${order.id}"),
                        actionButton = if (order.parsedStatus == OrderStatus.ARRIVED || order.parsedStatus == OrderStatus.EN_ROUTE) {
                            {
                                FilledTonalButton(
                                    onClick = { onOpenAcceptance(order.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_acceptance_btn_${order.id}")
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delivery Acceptance · Pay & Sign")
                                }
                            }
                        } else null,
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }
        } else {
            // DISTRIBUTOR MODE
            item {
                SectionHeaderRow(
                    title = "Incoming Nearby Commandes (${pendingQueue.size})",
                    subtitle = "Sorted by proximity to ${profile?.neighborhood ?: "Kigali"}"
                )
            }

            if (pendingDispatchState is UiState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (pendingQueue.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Listening for Kigali bar commandes...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "New orders from bars & shops appear here in real time. Tip: switch to Bar Owner to place a test commande.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                val sortedPending = pendingQueue.sortedBy {
                    KigaliCatalog.haversineKm(userLat, userLng, it.barLat, it.barLng)
                }
                items(sortedPending, key = { "pending_${it.id}" }) { order ->
                    val distKm = KigaliCatalog.haversineKm(userLat, userLng, order.barLat, order.barLng)
                    OrderSummaryCard(
                        order = order,
                        modifier = Modifier.testTag("pending_order_card_${order.id}"),
                        actionButton = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = "Distance",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "$distKm km",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onAcceptOrder(order) },
                                    enabled = !isActionLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("accept_order_btn_${order.id}")
                                ) {
                                    Text("Accept Commande")
                                }
                            }
                        },
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionHeaderRow(
                    title = "My Assigned Deliveries (${distOrders.size})",
                    subtitle = "Tap an active stop to update GPS status or verify delivery"
                )
            }

            if (distOrders.isEmpty()) {
                item {
                    EmptyOrdersIllustrationCard(
                        title = "No assigned deliveries",
                        subtitle = "Accept a nearby Kigali commande above to build your traffic-optimized delivery route.",
                        ctaLabel = "Open Route Optimizer",
                        onCtaClick = onOpenMapRoute
                    )
                }
            } else {
                items(distOrders, key = { "dist_${it.id}" }) { order ->
                    OrderSummaryCard(
                        order = order,
                        modifier = Modifier.testTag("dist_order_card_${order.id}"),
                        actionButton = if (order.parsedStatus == OrderStatus.ARRIVED || order.parsedStatus == OrderStatus.EN_ROUTE) {
                            {
                                FilledTonalButton(
                                    onClick = { onOpenAcceptance(order.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Complete Proof of Delivery")
                                }
                            }
                        } else null,
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun OperationsHeroCard(
    activeRole: UserRole,
    profile: UserProfile?,
    primaryMetricLabel: String,
    primaryMetricValue: String,
    secondaryMetricLabel: String,
    secondaryMetricValue: String,
    onPrimaryAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(KigaliEmeraldDeep, KigaliEmerald)
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.businessName?.ifBlank { activeRole.label } ?: activeRole.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "${profile?.neighborhood ?: "Kimihurura"}, Kigali · ${activeRole.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (activeRole == UserRole.BAR_OWNER) Icons.Filled.Storefront else Icons.Filled.LocalShipping,
                        contentDescription = activeRole.label,
                        tint = BrewAmberBright
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroStatChip(
                    label = primaryMetricLabel,
                    value = primaryMetricValue,
                    modifier = Modifier.weight(1f)
                )
                HeroStatChip(
                    label = secondaryMetricLabel,
                    value = secondaryMetricValue,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onPrimaryAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrewAmberBright,
                    contentColor = KigaliEmeraldDeep
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("hero_primary_action_button")
            ) {
                Icon(
                    imageVector = if (activeRole == UserRole.BAR_OWNER) Icons.Filled.Add else Icons.Filled.Route,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activeRole == UserRole.BAR_OWNER) "New Bralirwa Commande" else "Optimize Kigali Route",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.11f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = BrewAmberBright
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyOrdersIllustrationCard(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_orders_1795657401946),
                contentDescription = "Stacked beverage crates illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCtaClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(ctaLabel)
            }
        }
    }
}
