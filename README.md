# CanjeApp — Sistema de Canje de Puntos IoT

Aplicación Android (Kotlin) que se conecta por **Bluetooth Clásico (SPP)** a un punto de canje basado en **Arduino Uno + sensor ultrasónico HC-SR04**. Cuando el usuario acerca su teléfono a menos de 30 cm, la app genera un **código de un solo uso**, suma puntos y guarda el canje en **SQLite**. Desde la app también se controlan los actuadores del punto: LED de validación, buzzer y compuerta con servo.

**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — Unidad 2
**Integrantes:** Lindsay Soto · Matías Vargas — **Docente:** Igor Cáceres Padilla

## Pantallas

| # | Pantalla | Función |
|---|---|---|
| 1 | Inicio | Título del proyecto e integrantes |
| 2 | Control de acceso | Login local (SQLite + PBKDF2) con bloqueo tras 3 intentos |
| 2b | Registro | Alta de usuarios con política de credenciales |
| 3 | Proyecto | Problemática, solución, sensor HC-SR04, actuadores y comunicación |
| 4 | Monitoreo | Distancia en tiempo real, presencia, código de canje, puntos e historial |
| 5 | Control | LED (`1/0`), buzzer (`A/X`) y compuerta (`U/D`) por Bluetooth |


## Credenciales de demostración

| Usuario | Contraseña |
|---|---|
| `matias.vargas` | `Canje#2026` |
| `lindsay.soto` | `Canje#2026` |

Política: usuario `nombre.apellido` en minúsculas; contraseña de 8 o más caracteres con mayúscula, minúscula, número y símbolo.

## Requisitos y ejecución

1. Android Studio (AGP 9, JDK 17) y SDK 36. El teléfono debe tener Android 8.0 (API 26) o superior.
2. `File → Open` → carpeta `CanjeApp`, sincronizar Gradle y luego `Run ▶`.
3. Vincular el HC-05/HC-06 en *Ajustes → Bluetooth* (PIN `1234`).
4. Cargar `arduino/canje_iot/canje_iot.ino` en el Arduino Uno.

## Protocolo

| Arduino → App | App → Arduino |
|---|---|
| `Distancia: N cm` cada 500 ms | `1`/`0` LED · `A`/`X` buzzer · `U`/`D` compuerta |
| `>> <confirmación>` por cada comando | |

## Seguridad (ISO/IEC 27400)

Hash PBKDF2-HMAC-SHA256 con sal por usuario, bloqueo por intentos fallidos, sesión protegida en las pantallas internas, consultas SQL parametrizadas, permiso mínimo (`BLUETOOTH_CONNECT`) y `allowBackup=false`.
