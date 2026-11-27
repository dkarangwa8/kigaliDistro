package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.KigaliCatalog
import com.example.model.UserProfile
import com.example.model.UserRole

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    userEmail: String?,
    activeRole: UserRole,
    isSaving: Boolean,
    onSaveProfile: (
        displayName: String,
        role: UserRole,
        businessName: String,
        neighborhood: String,
        phone: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var businessName by remember(profile) { mutableStateOf(profile?.businessName ?: "") }
    var neighborhood by remember(profile) { mutableStateOf(profile?.neighborhood ?: "Kimihurura") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var selectedRole by remember(profile, activeRole) { mutableStateOf(activeRole) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Operational Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Switch between Bar/Shop Owner and Bralirwa Distributor mode anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = selectedRole == UserRole.BAR_OWNER,
                            onClick = { selectedRole = UserRole.BAR_OWNER },
                            label = { Text("Bar / Shop Owner") },
                            leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_bar_owner_chip")
                        )
                        FilterChip(
                            selected = selectedRole == UserRole.DISTRIBUTOR,
                            onClick = { selectedRole = UserRole.DISTRIBUTOR },
                            label = { Text("Distributor") },
                            leadingIcon = { Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_distributor_chip")
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Business & Kigali Location", style = MaterialTheme.typography.titleMedium)
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = "Signed in as $userEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(80) },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it.take(100) },
                        label = {
                            Text(if (selectedRole == UserRole.BAR_OWNER) "Bar / Shop Name" else "Bralirwa Depot / Hub Name")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(24) },
                        label = { Text("Rwanda Phone (+250 78... · Private)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Default Kigali Neighborhood", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(KigaliCatalog.neighborhoods) { area ->
                            FilterChip(
                                selected = neighborhood == area.name,
                                onClick = { neighborhood = area.name },
                                label = { Text(area.name) }
                            )
                        }
                    }

                    Button(
                        onClick = { onSaveProfile(displayName, selectedRole, businessName, neighborhood, phone) },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSaving) "Saving..." else "Save Business Profile")
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}
