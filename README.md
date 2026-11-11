# FaceSwap Android

Aplicación Android para crear intercambios faciales con procesamiento local.

## Motor local

La rama `feature/open-source-face-engine` integra:
- **MediaPipe Tasks Vision 0.10.21** para detectar y alinear los rostros.
- **OpenCV Android 4.10.0** para alineación, máscara y composición del resultado.
- **ONNX Runtime Android 1.20.0** para inferencia neuronal en el dispositivo.
- **GHOST 1 256 + CrossFace Ghost + ArcFace W600K R50** como pipeline neuronal: ArcFace extrae el vector de identidad, CrossFace lo adapta al espacio de GHOST y GHOST genera el rostro de destino.
- Descarga de modelos al almacenamiento privado con validación de integridad antes de utilizarlos.

Los modelos se cargan secuencialmente para reducir el uso de memoria. El primer uso requiere conexión y una descarga de gran tamaño; la inferencia se ejecuta localmente y no envía las fotos a un servidor.

## Estado de validación

La pantalla de intercambio ya está conectada a `NeuralFaceSwapEngine`, que ejecuta el pipeline ONNX y compone la salida sobre la foto de destino. Esta integración necesita completar la compilación de CI y pruebas con fotografías reales antes de considerarse validada para distribución. El rendimiento dependerá de la memoria y potencia del teléfono; equipos de gama baja pueden tardar bastante o no disponer de memoria suficiente.

## Licencias de los pesos

GHOST 1 está catalogado como Apache-2.0; CrossFace Ghost y ArcFace tienen términos propios (OpenRAIL-M y Non-Commercial, respectivamente). Los pesos no se empaquetan en el APK ni se redistribuyen. Consulta [MODEL_LICENSES.md](MODEL_LICENSES.md) antes de descargarlos o utilizarlos. El uso privado no elimina automáticamente los términos de cada modelo.

## Requisitos

- Android Studio reciente
- JDK 17
- Android SDK 35
- Android 8.0 o superior (API 26+)

## Compilar

```bash
./gradlew assembleDebug
```

## Privacidad y uso responsable

Las fotos se procesan en el dispositivo. Usa únicamente imágenes de personas que hayan dado su consentimiento; no crees contenido íntimo no consentido ni material destinado a suplantar o engañar.
