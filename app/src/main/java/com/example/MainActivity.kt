package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.DeliveryRepository
import com.example.data.signOutUser
import com.example.model.OrderStatus
import com.example.model.UserRole
import com.example.service.DeliveryNotificationHelper
import com.example.service.KigaliRouteOptimizer
import com.example.ui.AuthScreen
import com.example.ui.CreateOrderScreen
import com.example.ui.DeliveryAcceptanceScreen
import com.example.ui.LiveMapScreen
import com.example.ui.OrderDetailScreen
import com.example.ui.OrdersDashboardScreen
import com.example.ui.ProfileScreen
import com.example.ui.RatingsAndAlertsScreen
import com.example.ui.theme.KigaliDistroTheme
import com.example.viewmodel.DeliveryViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DISPATCH = "dispatch"
const val ROUTE_MAP = "live_map"
const val ROUTE_RATINGS = "ratings_alerts"
const val ROUTE_PROFILE = "profile"
const val ROUTE_CREATE_ORDER = "create_order"
const val ROUTE_ORDER_DETAIL = "order_detail/{orderId}"
const val ROUTE_ACCEPTANCE = "delivery_acceptance/{orderId}"

private data class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeliveryNotificationHelper.ensureChannel(this)
        setContent {
            KigaliDistroTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedKigaliDistroApp(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Kigali Operator",
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedKigaliDistroApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DeliveryViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DeliveryViewModel(
                    repository = DeliveryRepository(db),
                    routeOptimizer = KigaliRouteOptimizer(),
                    currentUserId = currentUserId,
                    defaultDisplayName = userDisplayName
                )
            }
        }
    )

    // Request POST_NOTIFICATIONS on Android 13+ for real-time delivery alerts
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val roleOverride by viewModel.selectedRoleOverride.collectAsStateWithLifecycle()
    val barOrdersState by viewModel.barOrdersState.collectAsStateWithLifecycle()
    val distOrdersState by viewModel.distributorOrdersState.collectAsStateWithLifecycle()
    val pendingDispatchState by viewModel.pendingDispatchState.collectAsStateWithLifecycle()
    val selectedOrderEventsState by viewModel.selectedOrderEventsState.collectAsStateWithLifecycle()
    val barRatingsState by viewModel.barSubmittedRatingsState.collectAsStateWithLifecycle()
    val distRatingsState by viewModel.distributorReceivedRatingsState.collectAsStateWithLifecycle()
    val multiStopPlan by viewModel.multiStopPlan.collectAsStateWithLifecycle()
    val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val inAppAlerts by viewModel.inAppAlerts.collectAsStateWithLifecycle()
    val latestSystemNotification by viewModel.latestSystemNotification.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val activeRole = roleOverride ?: profile?.parsedRole ?: UserRole.BAR_OWNER

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(latestSystemNotification) {
        latestSystemNotification?.let { alert ->
            DeliveryNotificationHelper.postStatusNotification(
                context = context,
                notificationId = alert.id.hashCode(),
                title = alert.title,
                body = alert.message
            )
            viewModel.consumeSystemNotification()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DISPATCH

    val topTabs = remember {
        listOf(
            TopTab(ROUTE_DISPATCH, "Dispatch", Icons.Filled.LocalShipping, Icons.Outlined.LocalShipping),
            TopTab(ROUTE_MAP, "Live Map", Icons.Filled.Map, Icons.Outlined.Map),
            TopTab(ROUTE_RATINGS, "Reviews", Icons.Filled.Star, Icons.Outlined.StarOutline),
            TopTab(ROUTE_PROFILE, "Business", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }
    val showMainChrome = currentRoute in topTabs.map { it.route }

    val activeOrdersForMap = remember(activeRole, barOrdersState, distOrdersState, pendingDispatchState) {
        val barList = (barOrdersState as? UiState.Success)?.data.orEmpty()
        val distList = (distOrdersState as? UiState.Success)?.data.orEmpty()
        val pendList = (pendingDispatchState as? UiState.Success)?.data.orEmpty()
        val combined = if (activeRole == UserRole.BAR_OWNER) barList else (distList + pendList).distinctBy { it.id }
        combined.filter { it.parsedStatus != OrderStatus.DELIVERED && it.parsedStatus != OrderStatus.CANCELLED }
    }

    val displayedRatings = if (activeRole == UserRole.DISTRIBUTOR) {
        (distRatingsState as? UiState.Success)?.data.orEmpty()
    } else {
        (barRatingsState as? UiState.Success)?.data.orEmpty()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isExpanded && showMainChrome) {
                NavigationRail {
                    topTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(ROUTE_DISPATCH) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (showMainChrome) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "KigaliDistro",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                // Instant Bar Owner <-> Distributor role switch pill
                                Surface(
                                    onClick = {
                                        val next = if (activeRole == UserRole.BAR_OWNER) UserRole.DISTRIBUTOR else UserRole.BAR_OWNER
                                        viewModel.switchRole(next)
                                    },
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .testTag("quick_role_switch_pill")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeRole == UserRole.BAR_OWNER) Icons.Filled.Storefront else Icons.Filled.LocalShipping,
                                            contentDescription = "Switch role",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (activeRole == UserRole.BAR_OWNER) "Bar Mode" else "Distributor",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (currentRoute == ROUTE_DISPATCH && activeRole == UserRole.BAR_OWNER) {
                        FloatingActionButton(
                            onClick = { navController.navigate(ROUTE_CREATE_ORDER) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_commande")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New Bralirwa Commande")
                        }
                    }
                },
                bottomBar = {
                    if (!isExpanded && showMainChrome) {
                        NavigationBar {
                            topTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(ROUTE_DISPATCH) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        if (tab.route == ROUTE_RATINGS && inAppAlerts.isNotEmpty()) {
                                            BadgedBox(badge = { Badge { Text("${inAppAlerts.size}") } }) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DISPATCH
                    ) {
                        composable(ROUTE_DISPATCH) {
                            OrdersDashboardScreen(
                                activeRole = activeRole,
                                profile = profile,
                                barOrdersState = barOrdersState,
                                distributorOrdersState = distOrdersState,
                                pendingDispatchState = pendingDispatchState,
                                isActionLoading = isActionLoading,
                                onCreateOrderClick = { navController.navigate(ROUTE_CREATE_ORDER) },
                                onOrderClick = { orderId ->
                                    viewModel.selectOrder(orderId)
                                    navController.navigate("order_detail/$orderId")
                                },
                                onAcceptOrder = { order -> viewModel.acceptPendingOrder(order) },
                                onOpenAcceptance = { orderId ->
                                    viewModel.selectOrder(orderId)
                                    navController.navigate("delivery_acceptance/$orderId")
                                },
                                onOpenMapRoute = {
                                    navController.navigate(ROUTE_MAP) {
                                        popUpTo(ROUTE_DISPATCH) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(ROUTE_MAP) {
                            LiveMapScreen(
                                activeRole = activeRole,
                                profile = profile,
                                activeOrders = activeOrdersForMap,
                                multiStopPlan = multiStopPlan,
                                hasGoogleMapsKey = viewModel.routeOptimizer.hasConfiguredMapsKey(),
                                isOptimizing = isActionLoading,
                                onOptimizeRoute = { lat, lng ->
                                    viewModel.optimizeDistributorMultiStopRoute(lat, lng)
                                },
                                onPublishLiveGps = { order, lat, lng ->
                                    val nextStatus = if (order.parsedStatus == OrderStatus.ACCEPTED || order.parsedStatus == OrderStatus.LOADING) {
                                        OrderStatus.EN_ROUTE
                                    } else order.parsedStatus
                                    viewModel.advanceOrderStatus(order, nextStatus, lat, lng)
                                },
                                onOrderClick = { orderId ->
                                    viewModel.selectOrder(orderId)
                                    navController.navigate("order_detail/$orderId")
                                }
                            )
                        }

                        composable(ROUTE_RATINGS) {
                            RatingsAndAlertsScreen(
                                activeRole = activeRole,
                                alerts = inAppAlerts,
                                ratings = displayedRatings,
                                onAlertClick = { orderId ->
                                    viewModel.selectOrder(orderId)
                                    navController.navigate("order_detail/$orderId")
                                }
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                profile = profile,
                                userEmail = userEmail,
                                activeRole = activeRole,
                                isSaving = isActionLoading,
                                onSaveProfile = { name, role, biz, area, phone ->
                                    viewModel.saveProfile(name, role, biz, area, phone)
                                },
                                onSignOut = {
                                    signOutUser(credentialManager = credentialManager, scope = scope)
                                }
                            )
                        }

                        composable(ROUTE_CREATE_ORDER) {
                            CreateOrderScreen(
                                profile = profile,
                                isSubmitting = isActionLoading,
                                onBack = { navController.popBackStack() },
                                onSubmitOrder = { barName, area, addr, cart, payMethod, notes ->
                                    viewModel.placeCommande(
                                        barName = barName,
                                        neighborhoodName = area,
                                        addressNote = addr,
                                        cartQuantities = cart,
                                        paymentMethod = payMethod,
                                        deliveryNotes = notes
                                    ) { newId ->
                                        viewModel.selectOrder(newId)
                                        navController.navigate("order_detail/$newId") {
                                            popUpTo(ROUTE_CREATE_ORDER) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable(
                            route = ROUTE_ORDER_DETAIL,
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { entry ->
                            val orderId = entry.arguments?.getString("orderId").orEmpty()
                            LaunchedEffect(orderId) { viewModel.selectOrder(orderId) }
                            val order = viewModel.findOrderById(orderId)
                            val events = (selectedOrderEventsState as? UiState.Success)?.data.orEmpty()
                            val allRatings = (barRatingsState as? UiState.Success)?.data.orEmpty() +
                                (distRatingsState as? UiState.Success)?.data.orEmpty()
                            val existingRating = allRatings.firstOrNull { it.orderId == orderId }

                            OrderDetailScreen(
                                order = order,
                                currentUserId = currentUserId,
                                events = events,
                                existingRating = existingRating,
                                isActionLoading = isActionLoading,
                                onBack = { navController.popBackStack() },
                                onAcceptOrder = { target -> viewModel.acceptPendingOrder(target) },
                                onAdvanceStatus = { target, next -> viewModel.advanceOrderStatus(target, next) },
                                onOpenAcceptance = { id -> navController.navigate("delivery_acceptance/$id") },
                                onCancelOrder = { target, reason -> viewModel.cancelOrder(target, reason) },
                                onSubmitRating = { target, stars, punct, hand, comment ->
                                    viewModel.submitRating(target, stars, punct, hand, comment) {}
                                }
                            )
                        }

                        composable(
                            route = ROUTE_ACCEPTANCE,
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { entry ->
                            val orderId = entry.arguments?.getString("orderId").orEmpty()
                            val order = viewModel.findOrderById(orderId)
                            DeliveryAcceptanceScreen(
                                order = order,
                                isSubmitting = isActionLoading,
                                onBack = { navController.popBackStack() },
                                onConfirmDelivery = { payMethod, payRef, recipient, sigPath, caption, photoB64, notes ->
                                    if (order != null) {
                                        viewModel.completeDeliveryAcceptance(
                                            order = order,
                                            paymentMethod = payMethod,
                                            paymentReference = payRef,
                                            recipientName = recipient,
                                            signatureSvgPath = sigPath,
                                            photoCaption = caption,
                                            photoThumbnailBase64 = photoB64,
                                            deliveryNotes = notes
                                        ) {
                                            navController.popBackStack()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
