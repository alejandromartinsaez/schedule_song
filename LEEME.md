# Alarma NFC (Android)

Despertador con horario semanal que reproduce una canción de Spotify con el volumen al máximo.
Al acercar tu etiqueta NFC se abre la app y te pregunta qué canción quieres para las alarmas de hoy.

## Cómo obtener el APK

**Opción A – GitHub (sin instalar nada):**
1. Crea un repositorio en github.com y sube esta carpeta tal cual (incluida `.github/`).
2. Pestaña **Actions** → «Compilar APK» se ejecuta solo (o pulsa *Run workflow*).
3. Cuando termine (~3 min), abre la ejecución y descarga **AlarmaNFC-apk** (un .zip con `app-debug.apk`).

**Opción B – Android Studio:** File → Open → esta carpeta → Build → Build App Bundle(s)/APK(s) → Build APK(s).

Para instalarlo en el móvil, permite «Instalar apps de origen desconocido» para el navegador o gestor de archivos.

## Primer uso
1. Abre **Alarma NFC** y concede todo lo que salga en «Permisos y ajustes» (sobre todo *Mostrar sobre otras apps*, *Pantalla completa* y *Sin restricciones de batería*).
2. Añade las horas de cada día en «Horario semanal».
3. «Vincular etiqueta» y acerca tu etiqueta NFC: la app guarda su ID y le graba un enlace para abrirse sola.
4. Cada día, acerca la etiqueta → elige la canción (Spotify → ⋯ → Compartir → Alarma NFC, o copia el enlace y vuelve).
5. «Probar la alarma ahora» para comprobar que todo funciona.

## Cómo funciona
- La canción elegida vale para las alarmas de ese día; si un día no eliges, se usa la última.
- A la hora: sube música y alarma al máximo, abre la canción en Spotify y, si a los 5 s no suena, le manda «play».
  Si a los 12 s sigue sin sonar nada (sin internet, Spotify cerrado sesión…), suena la alarma del sistema.
- «Detener» pausa la música y devuelve el volumen a como estaba. «Posponer» la repite a los 5 minutos.
- Necesita la app de Spotify instalada y con sesión iniciada. Con cuenta gratuita Spotify puede poner otra canción del mismo artista/lista en vez de la exacta.
- Android solo lee NFC con la pantalla encendida y desbloqueada.
