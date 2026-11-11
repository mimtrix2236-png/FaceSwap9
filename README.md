# FaceSwap Android

Aplicación Android para crear montajes de intercambio facial en fotografías y, en futuras versiones, vídeos.

## Estado
Base inicial del proyecto Android. La interfaz permite seleccionar imágenes y establece una arquitectura preparada para integrar un motor de procesamiento facial con consentimiento.

## Funciones previstas
- Importar una foto base y una foto de referencia desde la galería.
- Vista previa y exportación del resultado.
- Flujo de consentimiento y uso responsable.
- Procesamiento local como objetivo prioritario.
- Soporte de vídeo en una etapa posterior.

## Requisitos
- Android Studio reciente
- JDK 17
- Android SDK 35

## Compilar
Abrir el repositorio en Android Studio y ejecutar la configuración Gradle. También se puede compilar con:
```bash
./gradlew assembleDebug
```

## Privacidad y uso responsable
Usa únicamente imágenes de personas que hayan dado su consentimiento. La aplicación no está diseñada para crear contenido íntimo no consentido, suplantar identidades ni engañar sobre la autenticidad de un material.
