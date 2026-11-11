package com.mimtrix.faceswap

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceSwapApp() }
    }
}

@Composable
private fun FaceSwapApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseImage = remember { mutableStateOf<Uri?>(null) }
    val faceImage = remember { mutableStateOf<Uri?>(null) }
    val result = remember { mutableStateOf<Bitmap?>(null) }
    val processing = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("Selecciona las dos fotografías para comenzar.") }
    val error = remember { mutableStateOf(false) }
    val choosingBase = remember { mutableStateOf(true) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (choosingBase.value) baseImage.value = uri else faceImage.value = uri
        result.value = null
        error.value = false
        status.value = "Fotografía seleccionada. Puedes iniciar el intercambio."
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1020)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.faceswap_logo),
                        contentDescription = "FaceSwap logo rojo neón",
                        modifier = Modifier.height(52.dp)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("FaceSwap", color = Color.White, fontSize = 28.sp)
                        Text("ANDROID STUDIO • BETA", color = Color(0xFF9AA4BF), fontSize = 11.sp)
                    }
                }
                Text(
                    "Selecciona, compara y transforma tus fotografías.",
                    color = Color(0xFFD8DEF0),
                    fontSize = 17.sp
                )

                ImageSlot(
                    title = "1. Foto de destino",
                    subtitle = "La imagen donde irá el rostro",
                    selected = baseImage.value != null,
                    onClick = {
                        choosingBase.value = true
                        imagePicker.launch("image/*")
                    }
                )
                ImageSlot(
                    title = "2. Foto del rostro",
                    subtitle = "Imagen de referencia con permiso de la persona",
                    selected = faceImage.value != null,
                    onClick = {
                        choosingBase.value = false
                        imagePicker.launch("image/*")
                    }
                )

                Button(
                    onClick = {
                        val target = baseImage.value
                        val source = faceImage.value
                        if (target == null || source == null || processing.value) return@Button
                        processing.value = true
                        result.value = null
                        error.value = false
                        status.value = "Analizando los dos rostros..."
                        scope.launch {
                            try {
                                result.value = FaceSwapEngine(context).swap(target, source)
                                status.value = "Proceso terminado. Revisa el resultado."
                            } catch (e: Exception) {
                                error.value = true
                                status.value = e.message ?: "No se pudo completar el intercambio."
                            } finally {
                                processing.value = false
                            }
                        }
                    },
                    enabled = baseImage.value != null && faceImage.value != null && !processing.value,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (processing.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Text("  Procesando...")
                    } else {
                        androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Text("  Iniciar Face Swap")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (processing.value) "Procesando tus fotografías..." else if (result.value != null) "Resultado" else "Estado del procesamiento",
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            status.value,
                            color = if (error.value) Color(0xFFFF8A8A) else Color(0xFFB8C2D9),
                            fontSize = 14.sp
                        )
                        if (processing.value) {
                            CircularProgressIndicator(color = Color(0xFFB58AFF))
                        }
                        result.value?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Resultado del intercambio facial",
                                modifier = Modifier.fillMaxWidth().height(340.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                "Este prototipo usa detección facial y composición geométrica local; todavía no es un modelo neuronal de reemplazo de identidad.",
                                color = Color(0xFF9AA4BF),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Uso responsable", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Usa imágenes con permiso de las personas. No crees contenido íntimo sin consentimiento ni material destinado a suplantar o engañar.",
                            color = Color(0xFFB8C2D9),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSlot(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = Color(0xFFB58AFF)
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp)
                Text(
                    if (selected) "Imagen seleccionada" else subtitle,
                    color = Color(0xFFAAB4CC),
                    fontSize = 12.sp
                )
            }
            OutlinedButton(onClick = onClick, enabled = true) {
                Text(if (selected) "Cambiar" else "Elegir")
            }
        }
    }
}
