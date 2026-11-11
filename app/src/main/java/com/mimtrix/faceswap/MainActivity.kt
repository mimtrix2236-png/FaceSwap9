package com.mimtrix.faceswap

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mimtrix.faceswap.engine.NeuralFaceSwapEngine

private fun android.content.Context.decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension ||
        bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceSwapApp() }
    }
}

@Composable
private fun PhotoPreview(uri: Uri?, label: String, onChange: () -> Unit) {
    val context = LocalContext.current
    val preview by produceState<Bitmap?>(initialValue = null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { context.decodeScaledBitmap(uri, 720) }.getOrNull()
        }
    }
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(158.dp)
                    .background(Color(0xFF0B1020), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview!!.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null,
                        tint = Color(0xFF9D91FF), modifier = Modifier.size(36.dp))
                }
            }
            Text(label, color = Color.White, fontSize = 13.sp)
            OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
                Text(if (uri == null) "Elegir foto" else "Cambiar")
            }
        }
    }
}

@Composable
private fun FaceSwapApp() {
    val baseImage = remember { mutableStateOf<Uri?>(null) }
    val faceImage = remember { mutableStateOf<Uri?>(null) }
    val outputBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val status = remember { mutableStateOf("Elige las dos fotografías para comenzar.") }
    val processing = remember { mutableStateOf(false) }
    val tab = remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choosingBase = remember { mutableStateOf(true) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (choosingBase.value) baseImage.value = uri else faceImage.value = uri
        outputBitmap.value = null
        status.value = if (uri != null) "Fotografía cargada. Puedes verla en la vista previa." else "No se seleccionó ninguna imagen."
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1020)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.faceswap_logo),
                        contentDescription = "FaceSwap logo rojo neón",
                        modifier = Modifier.height(48.dp)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("FaceSwap", color = Color.White, fontSize = 28.sp)
                        Text("ANDROID STUDIO • BETA", color = Color(0xFF9AA4BF), fontSize = 11.sp)
                    }
                }
                Text("Selecciona, compara y transforma tus fotografías.",
                    color = Color(0xFFD8DEF0), fontSize = 16.sp)

                TabRow(selectedTabIndex = tab.intValue, containerColor = Color(0xFF0B1020),
                    contentColor = Color(0xFFB79BFF)) {
                    Tab(selected = tab.intValue == 0, onClick = { tab.intValue = 0 },
                        text = { Text("Editar") }, icon = { Icon(Icons.Rounded.Edit, null) })
                    Tab(selected = tab.intValue == 1, onClick = { tab.intValue = 1 },
                        text = { Text("Creación") }, icon = { Icon(Icons.Rounded.Collections, null) })
                }

                if (tab.intValue == 0) {
                    Text("1. Tus fotografías", color = Color.White, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoPreview(baseImage.value, "Foto de destino") {
                            choosingBase.value = true
                            imagePicker.launch("image/*")
                        }
                        PhotoPreview(faceImage.value, "Rostro de origen") {
                            choosingBase.value = false
                            imagePicker.launch("image/*")
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Uso responsable", color = Color.White, fontSize = 16.sp)
                            Text(
                                "Confirma que tienes permiso para usar ambas imágenes. No crees contenido íntimo sin consentimiento ni material destinado a suplantar o engañar.",
                                color = Color(0xFFB8C2D9), fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                processing.value = true
                                outputBitmap.value = null
                                tab.intValue = 1
                                status.value = "Preparando imágenes y detector facial…"
                                try {
                                    val sourceUri = faceImage.value ?: error("Selecciona la foto del rostro.")
                                    val targetUri = baseImage.value ?: error("Selecciona la foto de destino.")
                                    val sourceBitmap = withContext(Dispatchers.IO) {
                                        context.decodeScaledBitmap(sourceUri, 1600)
                                    } ?: error("No se pudo abrir la foto de referencia.")
                                    val targetBitmap = withContext(Dispatchers.IO) {
                                        context.decodeScaledBitmap(targetUri, 2048)
                                    } ?: error("No se pudo abrir la foto de destino.")
                                    try {
                                        status.value = "Analizando los dos rostros y preparando los modelos…"
                                        val result = NeuralFaceSwapEngine(context).swap(sourceBitmap, targetBitmap)
                                        outputBitmap.value = result
                                    } finally {
                                        sourceBitmap.recycle()
                                        targetBitmap.recycle()
                                    }
                                    status.value = "¡Transformación terminada! Tu resultado está aquí."
                                } catch (error: Exception) {
                                    status.value = error.message ?: "No se pudo procesar las imágenes."
                                } finally {
                                    processing.value = false
                                }
                            }
                        },
                        enabled = baseImage.value != null && faceImage.value != null && !processing.value,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7250B5))
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Text("  Crear intercambio de rostros", fontSize = 16.sp)
                    }
                    Text("Al iniciar, se abrirá automáticamente la pestaña Creación para que puedas seguir el proceso.",
                        color = Color(0xFF9AA4BF), fontSize = 12.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("2. Creación", color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.weight(1f))
                        if (outputBitmap.value != null) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Completado",
                                tint = Color(0xFF71D6A0))
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (processing.value) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = Color(0xFFB79BFF),
                                        modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                    Text("  Procesando tus fotografías…", color = Color.White)
                                }
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF9D7BE8), trackColor = Color(0xFF303A53))
                                Text("El procesamiento se realiza en este dispositivo. La imagen final aparecerá aquí cuando termine.",
                                    color = Color(0xFFB8C2D9), fontSize = 13.sp)
                            } else if (outputBitmap.value != null) {
                                Text("Resultado listo", color = Color(0xFF71D6A0), fontSize = 16.sp)
                                Image(bitmap = outputBitmap.value!!.asImageBitmap(),
                                    contentDescription = "Resultado del intercambio facial",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                                    contentScale = ContentScale.Fit)
                            } else {
                                Box(Modifier.fillMaxWidth().height(220.dp),
                                    contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                                            tint = Color(0xFF9D91FF), modifier = Modifier.size(42.dp))
                                        Text("Tu creación aparecerá aquí", color = Color.White)
                                        Text("Vuelve a Editar y pulsa Crear intercambio de rostros.",
                                            color = Color(0xFFB8C2D9), fontSize = 12.sp)
                                    }
                                }
                            }
                            Divider(color = Color(0xFF303A53))
                            Text(status.value, color = Color(0xFFB8C2D9), fontSize = 13.sp)
                        }
                    }
                    OutlinedButton(onClick = { tab.intValue = 0 },
                        modifier = Modifier.fillMaxWidth()) { Text("Volver a Editar") }
                }
            }
        }
    }
}
