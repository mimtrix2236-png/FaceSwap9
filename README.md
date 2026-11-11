# FaceSwap Android

Aplicación Android para crear montajes de intercambio facial con procesamiento local.

## Motor local

La rama `feature/open-source-face-engine` incluye:
- **MediaPipe Tasks Vision 0.10.21** para landmarks faciales.
- **OpenCV Android 4.10.0** para alineación geométrica y composición.
- **ONNX Runtime Android 1.20.0** como backend de inferencia neuronal en CPU.
- `NeuralModelStore`: descarga reanudable por archivo (temporal + validación SHA-256/MD5) a almacenamiento privado de la app.
- `NeuralModelRuntime`: apertura y cierre controlado de sesiones ONNX.

## Estado del motor neuronal

Se ha preparado el bundle experimental **GHOST 1 256 + CrossFace Ghost + ArcFace W600K R50**. GHOST está catalogado como Apache-2.0; CrossFace Ghost y ArcFace tienen términos propios (OpenRAIL-M y Non-Commercial, respectivamente). Los pesos no se empaquetan en el APK: se descargan desde sus fuentes upstream.

**Importante:** esta entrega incorpora el backend ONNX, el catálogo, la descarga verificada y la apertura de sesiones. El preprocesamiento tensorial exacto, la ejecución de los tres modelos y la composición neuronal todavía deben integrarse y validarse con imágenes de prueba. La pantalla actual continúa usando el primer pipeline geométrico MediaPipe + OpenCV; no debe presentarse aún como intercambio neuronal terminado.

Consulta [MODEL_LICENSES.md](MODEL_LICENSES.md) antes de descargar o usar esos pesos. La condición de uso privado no elimina automáticamente los términos de cada modelo. No se redistribuyen los pesos.

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

Las fotos se procesan en el dispositivo y no se envían a un servidor. La primera descarga de modelos requiere conexión y espacio libre considerable. Usa únicamente imágenes de personas que hayan dado su consentimiento; no crees contenido íntimo no consentido ni material destinado a suplantar o engañar.
