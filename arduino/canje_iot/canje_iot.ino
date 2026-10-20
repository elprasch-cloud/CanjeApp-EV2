/*
 * ============================================================================
 *  Sistema de Canje de Puntos IoT — Firmware Arduino Uno
 *  Asignatura: Aplicaciones Móviles para IoT (TI3042) — Unidad 2
 *  Integrantes: Lindsay Soto, Matías Vargas
 * ----------------------------------------------------------------------------
 *  Función:
 *   - Mide la distancia con el HC-SR04 cada 500 ms y la envía a la app
 *     Android por Bluetooth clásico (HC-05/HC-06, perfil SPP).
 *   - Recibe comandos de un carácter desde la app (pantalla "Sistema de
 *     Control") y acciona los actuadores del punto de canje.
 *
 *  Protocolo (UART 9600 baud, 8N1, una línea por mensaje terminada en \n):
 *   App -> Arduino            Arduino -> App
 *   '1'  LED validación ON    ">> LED ENCENDIDO"
 *   '0'  LED validación OFF   ">> LED APAGADO"
 *   'A'  Alarma (buzzer) ON   ">> ALARMA ACTIVADA"
 *   'X'  Alarma (buzzer) OFF  ">> ALARMA DESACTIVADA"
 *   'U'  Compuerta ABRIR      ">> COMPUERTA ABIERTA"
 *   'D'  Compuerta CERRAR     ">> COMPUERTA CERRADA"
 *   otro                      ">> COMANDO DESCONOCIDO"
 *   (periódico)               "Distancia: <n> cm"  |  "Distancia: -1 cm" (sin eco)
 *
 *  Conexionado:
 *   HC-SR04  VCC->5V  GND->GND  TRIG->D6  ECHO->D5
 *   HC-05    VCC->5V  GND->GND  TXD->D10  RXD<-D11 (divisor 1k/2k: 5V -> 3,3V)
 *   LED validación  D13 (LED integrado o LED + resistencia 220 Ω)
 *   Buzzer activo   D8
 *   Servo SG90      señal D9, VCC 5V, GND común
 * ============================================================================
 */

#include <SoftwareSerial.h>
#include <Servo.h>

// ---------------- Pines ----------------
const uint8_t PIN_BT_RX   = 10;   // Arduino RX  <- TXD del HC-05
const uint8_t PIN_BT_TX   = 11;   // Arduino TX  -> RXD del HC-05 (con divisor)
const uint8_t PIN_ECHO    = 5;
const uint8_t PIN_TRIG    = 6;
const uint8_t PIN_BUZZER  = 8;
const uint8_t PIN_SERVO   = 9;
const uint8_t PIN_LED     = 13;

// ---------------- Parámetros ----------------
const unsigned long PERIODO_LECTURA_MS = 500;    // envío de distancia
const unsigned long TIMEOUT_ECO_US     = 30000;  // ~5 m; evita bloquear 1 s si no hay eco
const uint8_t SERVO_CERRADO = 0;                 // grados
const uint8_t SERVO_ABIERTO = 90;

SoftwareSerial bluetooth(PIN_BT_RX, PIN_BT_TX);  // RX, TX
Servo compuerta;

unsigned long ultimoEnvio = 0;
bool alarmaActiva = false;

// Devuelve la distancia en cm o -1 si no hubo eco dentro del timeout.
int medirDistanciaCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  unsigned long duracion = pulseIn(PIN_ECHO, HIGH, TIMEOUT_ECO_US);
  if (duracion == 0) return -1;
  return (int)(duracion * 0.0343 / 2.0);   // velocidad del sonido 343 m/s
}

void procesarComando(char c) {
  switch (c) {
    case '1':
      digitalWrite(PIN_LED, HIGH);
      bluetooth.println(F(">> LED ENCENDIDO"));
      break;
    case '0':
      digitalWrite(PIN_LED, LOW);
      bluetooth.println(F(">> LED APAGADO"));
      break;
    case 'A':
      alarmaActiva = true;
      digitalWrite(PIN_BUZZER, HIGH);
      bluetooth.println(F(">> ALARMA ACTIVADA"));
      break;
    case 'X':
      alarmaActiva = false;
      digitalWrite(PIN_BUZZER, LOW);
      bluetooth.println(F(">> ALARMA DESACTIVADA"));
      break;
    case 'U':
      compuerta.write(SERVO_ABIERTO);
      bluetooth.println(F(">> COMPUERTA ABIERTA"));
      break;
    case 'D':
      compuerta.write(SERVO_CERRADO);
      bluetooth.println(F(">> COMPUERTA CERRADA"));
      break;
    case '\r':
    case '\n':
    case ' ':
      break;                      // ignora fin de línea si la app los envía
    default:
      bluetooth.println(F(">> COMANDO DESCONOCIDO"));
      break;
  }
}

void setup() {
  Serial.begin(9600);             // depuración por USB
  bluetooth.begin(9600);          // velocidad por defecto del HC-05/06

  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  digitalWrite(PIN_BUZZER, LOW);

  compuerta.attach(PIN_SERVO);
  compuerta.write(SERVO_CERRADO); // estado seguro al encender

  Serial.println(F("Punto de canje IoT listo"));
}

void loop() {
  // 1) Atender todos los comandos pendientes (no bloqueante)
  while (bluetooth.available() > 0) {
    char c = (char)bluetooth.read();
    Serial.print(F("CMD: "));
    Serial.println(c);
    procesarComando(c);
  }

  // 2) Telemetría periódica sin delay(): el Arduino sigue respondiendo comandos
  unsigned long ahora = millis();
  if (ahora - ultimoEnvio >= PERIODO_LECTURA_MS) {
    ultimoEnvio = ahora;
    int distancia = medirDistanciaCm();
    bluetooth.print(F("Distancia: "));
    bluetooth.print(distancia);
    bluetooth.println(F(" cm"));
  }
}
