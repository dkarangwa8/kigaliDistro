package com.example.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BralirwaProduct
import com.example.model.KigaliCatalog
import com.example.model.PaymentMethod
import com.example.model.UserProfile
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderScreen(
    profile: UserProfile?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSubmitOrder: (
        barName: String,
        neighborhood: String,
        addressNote: String,
        cart: Map<String, Int>,
        paymentMethod: PaymentMethod,
        deliveryNotes: String
    ) -> Unit
) {
    var barName by remember(profile) {
        mutableStateOf(profile?.businessName?.ifBlank { "Kimihurura Corner Bar" } ?: "Kimihurura Corner Bar")
    }
    var selectedNeighborhood by remember(profile) {
        mutableStateOf(profile?.neighborhood?.ifBlank { "Kimihurura" } ?: "Kimihurura")
    }
    var addressNote by remember { mutableStateOf("") }
    var deliveryNotes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.MOMO_MTN) }
    val cartQuantities = remember { mutableStateMapOf<String, Int>("PRI-72" to 2, "MUT-65" to 1) }

    val categories = listOf("All", "Beers", "Premium", "Soft Drinks")
    val filteredProducts = remember(selectedCategory) {
        if (selectedCategory == "All") KigaliCatalog.products
        else KigaliCatalog.products.filter { it.category == selectedCategory }
    }

    val totalCrates = cartQuantities.values.sum()
    val totalRwf = KigaliCatalog.products.sumOf { (cartQuantities[it.sku] ?: 0) * it.pricePerCrateRwf }
    val selectedArea = KigaliCatalog.findNeighborhood(selectedNeighborhood)
    val nearestDepot = KigaliCatalog.findNearestDepot(selectedArea.lat, selectedArea.lng)
    val depotDistKm = KigaliCatalog.haversineKm(nearestDepot.lat, nearestDepot.lng, selectedArea.lat, selectedArea.lng)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Bralirwa Commande") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_order_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$totalCrates crate(s) selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = KigaliCatalog.formatRwf(totalRwf),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            onSubmitOrder(
                                barName,
                                selectedNeighborhood,
                                addressNote,
                                cartQuantities.toMap(),
                                selectedPayment,
                                deliveryNotes
                            )
                        },
                        enabled = totalCrates > 0 && !isSubmitting && barName.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("submit_commande_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Commande", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
            // Destination Bar / Shop Details
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Destination Bar / Shop", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = barName,
                            onValueChange = { barName = it.take(100) },
                            label = { Text("Bar or Shop Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bar_name_input")
                        )
                        Text("Kigali Sector / Neighborhood", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(KigaliCatalog.neighborhoods) { area ->
                                FilterChip(
                                    selected = selectedNeighborhood == area.name,
                                    onClick = { selectedNeighborhood = area.name },
                                    label = { Text(area.name) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = addressNote,
                            onValueChange = { addressNote = it.take(200) },
                            label = { Text("Street / Landmark (e.g. KG 7 Ave near KCC)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.LocalShipping,
                                    contentDescription = "Nearest Depot",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Nearest Hub: ${nearestDepot.name}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$depotDistKm km away · Corridor: ${selectedArea.primaryCorridor}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Bralirwa Crates", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) }
                            )
                        }
                    }
                }
            }

            // Bralirwa Product Catalog
            items(filteredProducts, key = { it.sku }) { product ->
                ProductCrateRow(
                    product = product,
                    quantity = cartQuantities[product.sku] ?: 0,
                    onIncrement = {
                        val current = cartQuantities[product.sku] ?: 0
                        if (current < 100) cartQuantities[product.sku] = current + 1
                    },
                    onDecrement = {
                        val current = cartQuantities[product.sku] ?: 0
                        if (current > 1) cartQuantities[product.sku] = current - 1
                        else cartQuantities.remove(product.sku)
                    }
                )
            }

            // Preferred Settlement Upon Delivery
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Preferred Payment Upon Delivery", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PaymentMethod.entries) { method ->
                                FilterChip(
                                    selected = selectedPayment == method,
                                    onClick = { selectedPayment = method },
                                    label = { Text(method.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = deliveryNotes,
                            onValueChange = { deliveryNotes = it.take(400) },
                            label = { Text("Delivery Instructions (optional)") },
                            placeholder = { Text("e.g. Bring empty return crates, back entrance") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProductCrateRow(
    product: BralirwaProduct,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val isSelected = quantity > 0
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${product.packSize} · ${product.abvLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = KigaliCatalog.formatRwf(product.pricePerCrateRwf),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDecrement,
                    enabled = quantity > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dec_${product.sku}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease ${product.name}", modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inc_${product.sku}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Increase ${product.name}",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
