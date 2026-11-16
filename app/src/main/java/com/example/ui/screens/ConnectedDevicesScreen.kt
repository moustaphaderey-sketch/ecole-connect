package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ConnectedDeviceSession
import com.example.data.model.QrPairingRequest
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.google.firebase.Timestamp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ConnectedDevicesScreen(
    devices: List<ConnectedDeviceSession>,
    pendingInspection: QrPairingRequest?,
    isInspecting: Boolean,
    isBusy: Boolean,
    onInspectQrCode: (String) -> Unit,
    onDismissInspection: () -> Unit,
    onConfirmPairing: (QrPairingRequest) -> Unit,
    onRejectPairing: (QrPairingRequest) -> Unit,
    onRevokeDevice: (ConnectedDeviceSession) -> Unit,
    onRevokeAllDevices: () -> Unit,
    webPortalUrl: String = "https://gen-lang-client-0442156732.web.app"
) {
    val context = LocalContext.current
    var showScannerDialog by remember { mutableStateOf(false) }
    var confirmRevokeAll by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }

    val activeCount = remember(devices) { devices.count { !it.revoked } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(
                title = "Appareils connectés",
                subtitle = "Associez un navigateur Web par QR Code sécurisé et gérez vos sessions actives",
                icon = Icons.Filled.Devices
            )
        }

        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ÉCOLE CONNECTÉE Web",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activeCount session(s) ordinateur active(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Lien de la page Web (à ouvrir sur ordinateur)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SelectionContainer {
                                Text(
                                    text = webPortalUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("web_portal_url_text")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Lien ÉCOLE CONNECTÉE Web", webPortalUrl))
                                        linkCopied = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copy_web_link_button")
                                ) {
                                    Icon(
                                        imageVector = if (linkCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (linkCopied) "Lien copié !" else "Copier le lien")
                                }

                                IconButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(webPortalUrl))
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("open_web_link_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Ouvrir la page Web dans le navigateur",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (linkCopied) {
                                Text(
                                    text = "Lien copié dans le presse-papiers. Collez-le dans le navigateur de votre ordinateur.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("web_link_copied_feedback")
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showScannerDialog = true },
                        enabled = !isBusy && !isInspecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_device_button")
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Connecter un appareil")
                    }

                    if (activeCount > 0) {
                        OutlinedButton(
                            onClick = { confirmRevokeAll = true },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("revoke_all_devices_button")
                        ) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Déconnecter tous les ordinateurs")
                        }
                    }
                }
            }
        }

        if (isInspecting) {
            item {
                ElevatedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text(
                            text = "Vérification de la demande d'association Web...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (devices.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Computer,
                    title = "Aucun ordinateur associé",
                    subtitle = "Ouvrez ÉCOLE CONNECTÉE Web sur votre ordinateur puis appuyez sur « Connecter un appareil » pour scanner le QR Code."
                )
            }
        } else {
            items(devices, key = { it.id.ifBlank { it.sessionId } }) { session ->
                ConnectedDeviceSessionCard(
                    session = session,
                    isBusy = isBusy,
                    onDisconnect = { onRevokeDevice(session) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showScannerDialog) {
        QrScannerDialog(
            onDismiss = { showScannerDialog = false },
            onCodeDetected = { raw ->
                showScannerDialog = false
                onInspectQrCode(raw)
            }
        )
    }

    if (pendingInspection != null) {
        PairingConfirmationDialog(
            request = pendingInspection,
            onDismiss = onDismissInspection,
            onConfirm = { onConfirmPairing(pendingInspection) },
            onReject = { onRejectPairing(pendingInspection) }
        )
    }

    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { confirmRevokeAll = false },
            title = { Text("Déconnecter tous les ordinateurs ?") },
            text = {
                Text(
                    "Toutes les sessions Web associées à votre compte seront révoquées immédiatement côté serveur et ne pourront plus lire aucune donnée."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRevokeAll = false
                        onRevokeAllDevices()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_revoke_all_button")
                ) {
                    Text("Tout déconnecter")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeAll = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun ConnectedDeviceSessionCard(
    session: ConnectedDeviceSession,
    isBusy: Boolean,
    onDisconnect: () -> Unit
) {
    val isActive = !session.revoked
    val deviceTag = session.id.ifBlank { session.sessionId }

    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Column {
                        Text(
                            text = "${session.browserName.ifBlank { "Navigateur Web" }} • ${session.osName.ifBlank { "Ordinateur" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (session.userAgentLabel.isNotBlank()) {
                            Text(
                                text = session.userAgentLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isActive) "Actif" else "Déconnecté",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Date de connexion : ${formatDeviceTimestamp(session.connectedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Dernière activité : ${formatDeviceTimestamp(session.lastActiveAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isActive) {
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disconnect_device_$deviceTag")
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Déconnecter cet ordinateur")
                }
            }
        }
    }
}

@Composable
private fun QrScannerDialog(
    onDismiss: () -> Unit,
    onCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanner le QR Code Web") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasCameraPermission) {
                    Text(
                        text = "Pointez la caméra vers le QR Code affiché sur ÉCOLE CONNECTÉE Web (validité 120 secondes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        CameraQrPreviewBox(onQrScanned = onCodeDetected)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Autorisation caméra requise pour scanner automatiquement le QR Code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Autoriser la caméra")
                            }
                        }
                    }
                }

                Text(
                    text = "Ou saisir le code de demande affiché sous le QR Code :",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("Identifiant de demande QR") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_qr_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCodeDetected(manualCode) },
                enabled = manualCode.trim().length >= 8,
                modifier = Modifier.testTag("manual_qr_submit_button")
            ) {
                Text("Vérifier le code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun CameraQrPreviewBox(
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanned = remember { AtomicBoolean(false) }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdownNow()
        }
    }

    if (cameraError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = cameraError ?: "Caméra indisponible",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                try {
                                    if (!scanned.get()) {
                                        val decoded = decodeQrFromImageProxy(imageProxy, reader)
                                        if (!decoded.isNullOrBlank() && scanned.compareAndSet(false, true)) {
                                            previewView.post { onQrScanned(decoded) }
                                        }
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { err ->
                    cameraError = "Caméra non disponible sur cet appareil (${err.localizedMessage ?: "erreur"})."
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun decodeQrFromImageProxy(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val source = PlanarYUVLuminanceSource(
        data,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false
    )
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decodeWithState(bitmap)?.text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

@Composable
private fun PairingConfirmationDialog(
    request: QrPairingRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autoriser cet ordinateur ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Un navigateur demande à accéder à votre compte ÉCOLE CONNECTÉE :",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Navigateur : ${request.browserName.ifBlank { "Navigateur Web" }}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Système : ${request.osName.ifBlank { "Ordinateur" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (request.userAgentLabel.isNotBlank()) {
                            Text(
                                text = "Détails : ${request.userAgentLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    text = "Une session Web distincte et révocable à tout moment sera créée sans jamais transférer les jetons de votre téléphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_qr_pairing_button")
            ) {
                Text("Confirmer la connexion")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("reject_qr_pairing_button")
                ) {
                    Text("Refuser l'association")
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    )
}

private fun formatDeviceTimestamp(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "À l'instant"
    return SimpleDateFormat("dd MMM yyyy 'à' HH:mm", Locale.FRANCE).format(date)
}
