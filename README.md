# FaceSwap Android

Aplicación Android de intercambio facial con procesamiento neuronal local.

## Motor de intercambio

La aplicación integra un pipeline GHOST en el dispositivo:
- MediaPipe Face Landmarker detecta y alinea los rostros.
- ArcFace W600K R50 extrae la identidad del rostro de referencia.
- CrossFace adapta la identidad al espacio latente de GHOST.
- GHOST 1 256 genera el rostro para la fotografía de destino.
- OpenCV compone el resultado sobre la fotografía original.
- ONNX Runtime ejecuta la inferencia localmente.

Las fotografías no se envían a un servidor. En el primer uso, la aplicación descarga los modelos a su almacenamiento privado. Se necesita conexión a Internet durante esa descarga y aproximadamente 1 GB de espacio libre. Los modelos quedan almacenados para los usos siguientes. La pantalla informa la etapa y el porcentaje de descarga para evitar una espera sin explicación.

## Compilar

- Android Studio reciente
- JDK 17
- Android SDK 35

```bash
./gradlew assembleDebug
```

## Licencias de los modelos

Los pesos neuronales tienen términos propios, distintos de la licencia del código. Consulta [MODEL_LICENSES.md](MODEL_LICENSES.md) antes de redistribuir o usar comercialmente la aplicación. En particular, algunos pesos están sujetos a condiciones no comerciales.

## Uso responsable

Utiliza únicamente fotografías de personas que hayan dado su consentimiento. No crees contenido íntimo sin consentimiento ni material destinado a suplantar o engañar.
