package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DeliveryOrder
import com.example.model.KigaliCatalog
import com.example.model.PaymentMethod
import com.example.service.ProofMediaUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TrafficLightGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryAcceptanceScreen(
    order: DeliveryOrder?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onConfirmDelivery: (
        paymentMethod: PaymentMethod,
        paymentReference: String,
        recipientName: String,
        signatureSvgPath: String,
        photoCaption: String,
        photoThumbnailBase64: String,
        deliveryNotes: String
    ) -> Unit
) {
    if (order == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Delivery Acceptance") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Order not found.")
            }
        }
        return
    }

    val context = LocalContext.current

    var selectedPayment by remember(order.id) { mutableStateOf(order.parsedPaymentMethod) }
    var paymentReference by remember(order.id) {
        val prefix = when (order.parsedPaymentMethod) {
            PaymentMethod.MOMO_MTN -> "MOMO"
            PaymentMethod.AIRTEL_MONEY -> "ARTL"
            PaymentMethod.CARD_POS -> "POS"
            PaymentMethod.CASH_RWF -> "CASH"
        }
        val suffix = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
        mutableStateOf("$prefix-KGL-$suffix")
    }
    var recipientName by remember(order.id) { mutableStateOf("") }
    var photoCaption by remember(order.id) { mutableStateOf("${order.totalCrates} Bralirwa crates delivered intact") }
    var deliveryNotes by remember(order.id) { mutableStateOf("") }
    var photoBase64 by remember(order.id) { mutableStateOf("") }

    // Signature capture state
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ProofMediaUtils.uriToCompactBase64(context, uri)?.let { encoded ->
                photoBase64 = encoded
            }
        }
    }

    // Standard Camera Capture Preview (zero storage permission needed)
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            photoBase64 = ProofMediaUtils.bitmapToCompactBase64(bitmap)
        }
    }

    val decodedPreview = remember(photoBase64) {
        ProofMediaUtils.decodeBase64ToImageBitmap(photoBase64)
    }

    val hasSignature = completedStrokes.isNotEmpty()
    val hasPhoto = photoBase64.isNotBlank()
    val hasPaymentRef = paymentReference.trim().length >= 4
    val hasRecipient = recipientName.trim().length >= 2
    val readyToSubmit = hasSignature && hasPhoto && hasPaymentRef && hasRecipient && !isSubmitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivery Acceptance") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("acceptance_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val missingSteps = buildList {
                        if (!hasPaymentRef) add("Payment Ref")
                        if (!hasRecipient) add("Recipient")
                        if (!hasSignature) add("Signature")
                        if (!hasPhoto) add("Photo Proof")
                    }
                    if (missingSteps.isNotEmpty()) {
                        Text(
                            text = "Required to complete: ${missingSteps.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val sigPath = ProofMediaUtils.serializeSignatureStrokes(
                                strokes = completedStrokes.toList(),
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight
                            )
                            onConfirmDelivery(
                                selectedPayment,
                                paymentReference,
                                recipientName,
                                sigPath,
                                photoCaption,
                                photoBase64,
                                deliveryNotes
                            )
                        },
                        enabled = readyToSubmit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_delivery_acceptance_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Verifying & Saving...")
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm Delivery & Settle Payment", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Order Handover Summary
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${order.barName} · ${order.barNeighborhood}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = order.itemsSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${order.totalCrates} Crates Verified",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = KigaliCatalog.formatRwf(order.totalAmountRwf),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Step 2: Digital Payment Settlement upon Delivery
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("1. Digital Payment Upon Delivery", style = MaterialTheme.typography.titleMedium)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PaymentMethod.entries) { method ->
                                FilterChip(
                                    selected = selectedPayment == method,
                                    onClick = {
                                        selectedPayment = method
                                        val prefix = when (method) {
                                            PaymentMethod.MOMO_MTN -> "MOMO"
                                            PaymentMethod.AIRTEL_MONEY -> "ARTL"
                                            PaymentMethod.CARD_POS -> "POS"
                                            PaymentMethod.CASH_RWF -> "CASH"
                                        }
                                        val suffix = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
                                        paymentReference = "$prefix-KGL-$suffix"
                                    },
                                    label = { Text(method.label) }
                                )
                            }
                        }

                        selectedPayment.ussdCode?.let { ussd ->
                            OutlinedButton(
                                onClick = {
                                    val encoded = Uri.encode(ussd)
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encoded"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("launch_momo_ussd_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open ${selectedPayment.label} USSD ($ussd)")
                            }
                        }

                        OutlinedTextField(
                            value = paymentReference,
                            onValueChange = { paymentReference = it.uppercase(Locale.US).take(80) },
                            label = { Text("Transaction / Receipt Reference") },
                            supportingText = { Text("Enter MoMo TXN ID, POS slip code, or cash receipt ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_reference_input")
                        )
                    }
                }
            }

            // Step 3: Interactive Signature Capture Module
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("2. Recipient Signature Capture", style = MaterialTheme.typography.titleMedium)
                            }
                            if (hasSignature) {
                                IconButton(
                                    onClick = {
                                        completedStrokes.clear()
                                        activeStroke = emptyList()
                                    },
                                    modifier = Modifier.testTag("clear_signature_button")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear signature")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it.take(100) },
                            label = { Text("Recipient Full Name (Bar Owner / Manager)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recipient_name_input")
                        )

                        val inkColor = MaterialTheme.colorScheme.onSurface
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .border(
                                    width = if (hasSignature) 2.dp else 1.dp,
                                    color = if (hasSignature) TrafficLightGreen else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .onSizeChanged {
                                    canvasWidth = it.width.toFloat()
                                    canvasHeight = it.height.toFloat()
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            activeStroke = listOf(startOffset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStroke = activeStroke + change.position
                                        },
                                        onDragEnd = {
                                            if (activeStroke.isNotEmpty()) {
                                                completedStrokes.add(activeStroke)
                                                activeStroke = emptyList()
                                            }
                                        },
                                        onDragCancel = {
                                            if (activeStroke.isNotEmpty()) {
                                                completedStrokes.add(activeStroke)
                                                activeStroke = emptyList()
                                            }
                                        }
                                    )
                                }
                                .testTag("signature_capture_pad")
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Signature guide baseline
                                val baselineY = size.height * 0.78f
                                drawLine(
                                    color = inkColor.copy(alpha = 0.16f),
                                    start = Offset(24.dp.toPx(), baselineY),
                                    end = Offset(size.width - 24.dp.toPx(), baselineY),
                                    strokeWidth = 1.dp.toPx()
                                )

                                val allStrokes = completedStrokes + listOf(activeStroke)
                                allStrokes.forEach { pts ->
                                    if (pts.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(pts.first().x, pts.first().y)
                                            pts.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                                        }
                                        drawPath(
                                            path = path,
                                            color = inkColor,
                                            style = Stroke(
                                                width = 4.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    } else if (pts.size == 1) {
                                        drawCircle(inkColor, radius = 2.5.dp.toPx(), center = pts.first())
                                    }
                                }
                            }
                            if (!hasSignature && activeStroke.isEmpty()) {
                                Text(
                                    text = "Sign here with your finger to confirm crate count",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }

            // Step 4: Photo Upload Verification Module
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("3. Photo Upload Verification", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Capture or upload a clear photo of the delivered Bralirwa crates at ${order.barName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { cameraPreviewLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("capture_delivery_photo_button")
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Take Photo")
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pick_delivery_photo_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Photo Picker")
                            }
                        }

                        if (decodedPreview != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, TrafficLightGreen, RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    bitmap = decodedPreview,
                                    contentDescription = "Uploaded delivery crate verification photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = TrafficLightGreen,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "PHOTO VERIFIED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = photoCaption,
                            onValueChange = { photoCaption = it.take(200) },
                            label = { Text("Photo Verification Caption") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = deliveryNotes,
                            onValueChange = { deliveryNotes = it.take(400) },
                            label = { Text("Handover Remarks (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
