# FaceSwap Android

Aplicación Android para crear montajes de intercambio facial en fotografías con procesamiento local.

## Motor local integrado

La rama `feature/open-source-face-engine` incorpora:
- **MediaPipe Tasks Vision 0.10.21** para detectar puntos faciales (landmarks) en el dispositivo.
- **OpenCV Android 4.10.0** para alineación geométrica y mezcla Poisson/seamless clone.
- Descarga inicial y caché local del modelo Face Landmarker oficial; las fotos se procesan en el teléfono y no se envían a un servidor.
- Interfaz Android conectada al flujo de procesamiento y vista previa.

Este primer motor usa alineación geométrica clásica y mezcla de imagen; no es todavía un modelo generativo neuronal de identidad. La calidad depende de que ambas fotos tengan rostros frontales, bien iluminados y con escala/pose parecidas. El primer uso necesita conexión para descargar el modelo; después queda en caché.

## Licencias

- OpenCV Android: Apache-2.0.
- MediaPipe Tasks: Apache-2.0.
- Los modelos de intercambio facial de InsightFace/InSwapper y algunos modelos de FaceFusion tienen restricciones no comerciales o requieren licencia adicional; por eso **no se incluyen** en esta rama.

Conserva los avisos de licencia y atribución de las dependencias al distribuir la aplicación. Verifica también las condiciones de redistribución del archivo de modelo concreto antes de empaquetarlo dentro del APK.

## Requisitos

- Android Studio reciente
- JDK 17
- Android SDK 35
- Android 8.0 o superior (API 26+)

## Compilar

Abrir el repositorio en Android Studio y ejecutar la sincronización Gradle. También se puede compilar con:

```bash
./gradlew assembleDebug
```

## Uso responsable y privacidad

Usa únicamente imágenes de personas que hayan dado su consentimiento. No crees contenido íntimo no consentido, suplantaciones ni material engañoso. El procesamiento de las fotografías es local; el modelo de puntos faciales se descarga una vez desde el alojamiento oficial de MediaPipe.
