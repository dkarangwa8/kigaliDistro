package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.DeliveryOrder
import com.example.model.KigaliCatalog
import com.example.model.OrderStatus
import com.example.model.TrafficLevel
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.service.MultiStopRoutePlan
import com.example.ui.components.StatusPill
import com.example.ui.components.TrafficPill
import com.example.ui.theme.BrewAmberBright
import com.example.ui.theme.BrewGold
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KigaliEmerald
import com.example.ui.theme.KigaliEmeraldDeep
import com.example.ui.theme.KigaliTerracotta
import com.example.ui.theme.TrafficHeavyRed
import com.example.ui.theme.TrafficLightGreen
import com.example.ui.theme.TrafficModerateAmber
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@SuppressLint("MissingPermission")
@Composable
fun LiveMapScreen(
    activeRole: UserRole,
    profile: UserProfile?,
    activeOrders: List<DeliveryOrder>,
    multiStopPlan: MultiStopRoutePlan?,
    hasGoogleMapsKey: Boolean,
    isOptimizing: Boolean,
    onOptimizeRoute: (Double, Double) -> Unit,
    onPublishLiveGps: (DeliveryOrder, Double, Double) -> Unit,
    onOrderClick: (String) -> Unit
) {
    val context = LocalContext.current
    var liveLatLng by remember(profile) {
        mutableStateOf(LatLng(profile?.lat ?: -1.9536, profile?.lng ?: 30.0928))
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val fetchCurrentGps: (onLocation: (Double, Double) -> Unit) -> Unit = { callback ->
        if (hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && loc.latitude in -2.5..-1.3 && loc.longitude in 29.0..31.0) {
                    liveLatLng = LatLng(loc.latitude, loc.longitude)
                    callback(loc.latitude, loc.longitude)
                } else {
                    callback(liveLatLng.latitude, liveLatLng.longitude)
                }
            }.addOnFailureListener {
                callback(liveLatLng.latitude, liveLatLng.longitude)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            callback(liveLatLng.latitude, liveLatLng.longitude)
        }
    }

    val focusedOrder = activeOrders.firstOrNull {
        it.parsedStatus in listOf(OrderStatus.EN_ROUTE, OrderStatus.ARRIVED, OrderStatus.LOADING, OrderStatus.ACCEPTED)
    } ?: activeOrders.firstOrNull()

    val kigaliCenter = remember(focusedOrder) {
        if (focusedOrder != null) LatLng(focusedOrder.barLat, focusedOrder.barLng)
        else LatLng(-1.9536, 30.0880)
    }

    val routePolyline = remember(multiStopPlan, focusedOrder) {
        when {
            multiStopPlan != null && multiStopPlan.polylinePoints.size >= 2 -> multiStopPlan.polylinePoints
            focusedOrder != null -> listOf(
                LatLng(focusedOrder.driverLat, focusedOrder.driverLng),
                LatLng((focusedOrder.driverLat + focusedOrder.barLat) / 2.0 + 0.002, (focusedOrder.driverLng + focusedOrder.barLng) / 2.0 - 0.002),
                LatLng(focusedOrder.barLat, focusedOrder.barLng)
            )
            else -> emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Interactive Map Viewport
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                if (hasGoogleMapsKey) {
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(kigaliCenter, 13.2f)
                    }
                    LaunchedEffect(kigaliCenter) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(kigaliCenter, 13.2f)
                    }
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isTrafficEnabled = true,
                            isMyLocationEnabled = hasLocationPermission
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            myLocationButtonEnabled = false,
                            compassEnabled = true
                        )
                    ) {
                        KigaliCatalog.depots.forEach { depot ->
                            Marker(
                                state = MarkerState(position = LatLng(depot.lat, depot.lng)),
                                title = depot.name,
                                snippet = "Bralirwa Depot · ${depot.neighborhood}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }
                        activeOrders.forEach { order ->
                            Marker(
                                state = MarkerState(position = LatLng(order.barLat, order.barLng)),
                                title = order.barName,
                                snippet = "${order.totalCrates} crates · ${order.parsedStatus.label}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                            )
                            if (order.distributorId.isNotBlank()) {
                                Marker(
                                    state = MarkerState(position = LatLng(order.driverLat, order.driverLng)),
                                    title = "Delivery: ${order.distributorName}",
                                    snippet = "ETA ${order.etaMinutes} min · ${order.parsedTrafficLevel.label}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                )
                            }
                        }
                        if (routePolyline.size >= 2) {
                            val trafficColor = when (multiStopPlan?.trafficLevel ?: focusedOrder?.parsedTrafficLevel ?: TrafficLevel.MODERATE) {
                                TrafficLevel.LIGHT -> TrafficLightGreen
                                TrafficLevel.MODERATE -> TrafficModerateAmber
                                TrafficLevel.HEAVY -> TrafficHeavyRed
                            }
                            Polyline(
                                points = routePolyline,
                                color = trafficColor,
                                width = 12f
                            )
                        }
                    }
                } else {
                    KigaliInteractiveVectorMap(
                        activeOrders = activeOrders,
                        routePoints = routePolyline,
                        trafficLevel = multiStopPlan?.trafficLevel ?: focusedOrder?.parsedTrafficLevel ?: TrafficLevel.MODERATE
                    )
                }

                // Floating Traffic & Maps Status Badge
                Surface(
                    color = KigaliEmeraldDeep.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Traffic,
                            contentDescription = "Live Traffic",
                            tint = BrewAmberBright,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasGoogleMapsKey) "Google Maps · Live Kigali Traffic" else "Kigali Dispatch Map · Add MAPS_API_KEY for Live Tiles",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Route Optimization & Live GPS Control Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeRole == UserRole.DISTRIBUTOR) "Multi-Stop Route Optimizer" else "Live Delivery Tracking",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = multiStopPlan?.routeSummary
                                    ?: focusedOrder?.routeSummary?.ifBlank { "Traffic-aware routing across Kigali corridors" }
                                    ?: "Kigali real-time traffic corridor monitoring",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TrafficPill(
                            level = multiStopPlan?.trafficLevel ?: focusedOrder?.parsedTrafficLevel ?: TrafficLevel.MODERATE,
                            etaMinutes = (multiStopPlan?.totalEtaMinutes?.toLong() ?: focusedOrder?.etaMinutes ?: 14L)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                fetchCurrentGps { lat, lng ->
                                    onOptimizeRoute(lat, lng)
                                }
                            },
                            enabled = !isOptimizing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("optimize_route_button")
                        ) {
                            Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isOptimizing) "Optimizing..." else "Optimize Route")
                        }

                        if (activeRole == UserRole.DISTRIBUTOR && focusedOrder != null && focusedOrder.distributorId.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    fetchCurrentGps { lat, lng ->
                                        onPublishLiveGps(focusedOrder, lat, lng)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("publish_gps_button")
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync GPS")
                            }
                        }
                    }
                }
            }
        }

        // Optimized Stop Sequence
        val displayStops = multiStopPlan?.orderedStops?.takeIf { it.isNotEmpty() } ?: activeOrders
        item {
            Text(
                text = if (multiStopPlan != null && multiStopPlan.orderedStops.isNotEmpty())
                    "Optimized Delivery Sequence (${multiStopPlan.totalDistanceKm} km · ${multiStopPlan.totalEtaMinutes} min)"
                else
                    "Active Kigali Stops (${displayStops.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (displayStops.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No active deliveries on the map yet. Place or accept a Bralirwa commande to see live traffic routing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(displayStops, key = { _, o -> "stop_${o.id}" }) { index, order ->
                Card(
                    onClick = { onOrderClick(order.id) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(order.barName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${order.barNeighborhood} · ${order.distanceKm} km · ETA ${order.etaMinutes}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(status = order.parsedStatus)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun KigaliInteractiveVectorMap(
    activeOrders: List<DeliveryOrder>,
    routePoints: List<LatLng>,
    trafficLevel: TrafficLevel
) {
    // Kigali geographic viewport bounds
    val minLat = -1.995
    val maxLat = -1.915
    val minLng = 30.035
    val maxLng = 30.145

    fun project(lat: Double, lng: Double, width: Float, height: Float): Offset {
        val x = (((lng - minLng) / (maxLng - minLng)).coerceIn(0.05, 0.95) * width).toFloat()
        val y = ((1.0 - ((lat - minLat) / (maxLat - minLat)).coerceIn(0.05, 0.95)) * height).toFloat()
        return Offset(x, y)
    }

    val routeColor = when (trafficLevel) {
        TrafficLevel.LIGHT -> TrafficLightGreen
        TrafficLevel.MODERATE -> BrewGold
        TrafficLevel.HEAVY -> TrafficHeavyRed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0E2921), Color(0xFF153B30), Color(0xFF0B221B))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle topographic grid lines representing Kigali hills
            val gridColor = Color.White.copy(alpha = 0.06f)
            for (i in 1..6) {
                val x = size.width * i / 7f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                val y = size.height * i / 7f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            // Major Kigali arteries (KN 3 Rd, KG 7 Ave, KG 11 Ave, KK 15 Rd)
            val arteries = listOf(
                listOf(LatLng(-1.9397, 30.0445), LatLng(-1.9499, 30.0588), LatLng(-1.9536, 30.0928), LatLng(-1.9579, 30.1086), LatLng(-1.9708, 30.1355)),
                listOf(LatLng(-1.9225, 30.0942), LatLng(-1.9355, 30.0825), LatLng(-1.9536, 30.0928), LatLng(-1.9845, 30.1032)),
                listOf(LatLng(-1.9695, 30.0748), LatLng(-1.9536, 30.0928), LatLng(-1.9430, 30.1165))
            )
            arteries.forEach { road ->
                val path = Path()
                road.forEachIndexed { idx, pt ->
                    val pos = project(pt.latitude, pt.longitude, size.width, size.height)
                    if (idx == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.17f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Traffic-colored optimized route polyline
            if (routePoints.size >= 2) {
                val path = Path()
                routePoints.forEachIndexed { idx, pt ->
                    val pos = project(pt.latitude, pt.longitude, size.width, size.height)
                    if (idx == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y)
                }
                drawPath(
                    path = path,
                    color = routeColor.copy(alpha = 0.35f),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = routeColor,
                    style = Stroke(
                        width = 4.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))
                    )
                )
            }

            // Bralirwa Depots (Emerald rings)
            KigaliCatalog.depots.forEach { depot ->
                val p = project(depot.lat, depot.lng, size.width, size.height)
                drawCircle(color = Color(0xFF5CD6B0).copy(alpha = 0.25f), radius = 12.dp.toPx(), center = p)
                drawCircle(color = Color(0xFF5CD6B0), radius = 5.dp.toPx(), center = p)
            }

            // Active Order Bar Destinations (Gold) & Live Driver Vehicles (Terracotta/White)
            activeOrders.forEach { order ->
                val barPt = project(order.barLat, order.barLng, size.width, size.height)
                drawCircle(color = BrewAmberBright.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = barPt)
                drawCircle(color = BrewAmberBright, radius = 7.dp.toPx(), center = barPt)

                if (order.distributorId.isNotBlank()) {
                    val drvPt = project(order.driverLat, order.driverLng, size.width, size.height)
                    drawCircle(color = KigaliTerracotta.copy(alpha = 0.35f), radius = 15.dp.toPx(), center = drvPt)
                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = drvPt)
                    drawCircle(color = KigaliTerracotta, radius = 5.dp.toPx(), center = drvPt)
                }
            }
        }

        // Map Legend Pill at Bottom
        Surface(
            color = KigaliEmeraldDeep.copy(alpha = 0.85f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(Color(0xFF5CD6B0), "Depot")
                LegendDot(BrewAmberBright, "Bar/Shop")
                LegendDot(KigaliTerracotta, "Live Truck")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
