# Subtítulos ES

App Android que muestra subtítulos flotantes en español sobre cualquier video en inglés (pensada para Dailymotion).

Todo corre en el teléfono, sin claves de API:

1. **Captura** el audio que suena en el teléfono (MediaProjection + AudioPlaybackCapture, Android 10+).
2. **Transcribe** en inglés con [Vosk](https://alphacephei.com/vosk/) (modelo pequeño, se descarga la primera vez).
3. **Traduce** inglés → español con ML Kit (modelo offline, se descarga la primera vez).
4. **Muestra** la traducción en una franja flotante.

## Obtener el APK

Cada push compila automáticamente con GitHub Actions. Entra a la pestaña **Actions**, abre la última ejecución y descarga el artefacto `subtitulos-es-debug` (contiene `app-debug.apk`).

## Limitaciones conocidas

- Si Dailymotion bloquea la captura de su audio, no habrá subtítulos.
- Los subtítulos llevan un par de segundos de retraso.
- Aún no hay voz en español (solo subtítulos).
