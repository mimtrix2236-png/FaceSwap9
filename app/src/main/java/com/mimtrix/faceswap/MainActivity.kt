package com.mimtrix.faceswap

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceSwapApp() }
    }
}

@Composable
private fun FaceSwapApp() {
    val baseImage = remember { mutableStateOf<Uri?>(null) }
    val faceImage = remember { mutableStateOf<Uri?>(null) }
    val picker = remember { mutableStateOf(false) }
    val choosingBase = remember { mutableStateOf(true) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (choosingBase.value) baseImage.value = uri else faceImage.value = uri
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B1020)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.faceswap_logo),
                        contentDescription = "FaceSwap logo rojo neón",
                        modifier = Modifier.height(48.dp)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("FaceSwap", color = Color.White, fontSize = 28.sp)
                        Text("ANDROID STUDIO • BETA", color = Color(0xFF9AA4BF), fontSize = 11.sp)
                    }
                }
                Text(
                    "Crea montajes con tus propias imágenes.",
                    color = Color(0xFFD8DEF0),
                    fontSize = 16.sp
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171F35)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Uso responsable", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Confirma que tienes permiso para usar ambas imágenes. No crees contenido íntimo sin consentimiento ni material destinado a suplantar o engañar.",
                            color = Color(0xFFB8C2D9),
                            fontSize = 13.sp
                        )
                    }
                }
                Button(
                    onClick = { },
                    enabled = baseImage.value != null && faceImage.value != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Text("  Preparar intercambio")
                }
                Text(
                    "Versión inicial: selección de imágenes lista. El motor de intercambio y la exportación se integrarán en la siguiente etapa.",
                    color = Color(0xFF8994AE),
                    fontSize = 12.sp
                )
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
                tint = Color(0xFF9D91FF)
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp)
                Text(
                    if (selected) "Imagen seleccionada" else subtitle,
                    color = Color(0xFFAAB4CC),
                    fontSize = 12.sp
                )
            }
            OutlinedButton(onClick = onClick) {
                Text(if (selected) "Cambiar" else "Elegir")
            }
        }
    }
}
