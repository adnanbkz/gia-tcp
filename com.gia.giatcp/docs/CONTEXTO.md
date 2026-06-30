# GIA TCP Calibrator — Contexto y estado actual

> Documento de contexto/handoff. Resume lo importante del proyecto y el punto exacto en el
> que estamos. Última actualización: 2026-06-17.

---

## 1. Qué es el proyecto

URCap **standalone** de marca GIA-ROBOTICS que clona la funcionalidad del **CAPTRON TCP**
(calibrador de TCP por barreras de luz). Proyecto NUEVO e independiente de GIAWeld.

- **Ubicación:** `/home/adnan/IdeaProjects/giatcp/com.gia.giatcp` (NO está dentro del repo git de
  giaweld; las borrados aquí no son recuperables por git).
- **Paquete:** `com.GIA.GIATcp` · symbolicName `com.GIA.GIATcp` · salida `target/GIA-TCP-1.0.urcap`.
- **Sin licencia** (la clave CAPTRON `CC8871EB` no puede autorizar un URCap de GIA).
- **Plan original:** `/home/adnan/.claude/plans/valiant-crunching-island.md`.

### Para qué sirve en la célula real
Célula Pronimetal "Estación_Cobot_HEM": estación doble de soldadura, UR sobre eje lineal FESTO de
6 m, antorcha KEMPPI, software GIA GWT. El calibrador de TCP es una **estación fija** a la que la
antorcha se aproxima para medir y corregir el TCP real del hilo/antorcha.

---

## 2. Hardware real (definido y confirmado)

**2× SensoPart FGL 50-IK-50-PS-M4** (ref. 832-11022) cruzados a 90°, NO el sensor CAPTRON.

- Cada uno es una **barrera de horquilla IR de UN SOLO HAZ**: salida PNP (N.O./N.C.), conector M8
  4-pin, respuesta 2 kHz (0,5 ms). El cuello de botella real es el bucle de control del UR
  (~500 Hz) + filtro de entrada digital → poner filtro al mínimo y sondear lento.
- **Dos forks cruzados** = arquitectura de dos barreras (`ioX` + `ioY`, sondeo circular,
  intersección de 2 líneas). Es la correcta; NO hay que reescribir el algoritmo, solo adaptar a
  SensoPart.
- **Polaridad (no obvio):** el algoritmo asume **entrada HIGH = haz cortado**. Los FGL 50 deben ir
  en **dark-operate** (PNP 24 V cuando el haz está bloqueado). Validable en la pantalla Diagnostics.
- Montaje mecánico (lo dirige GIA desde el algoritmo): `docs/montaje_calibrador_2sensores.md`.

---

## 3. Pivote de arquitectura del jefe (Albert Olivé, 2026-06-15)

**Mover TODA la matemática a Java** para poder reutilizar el código en **Estun** (otro robot).

- En **URScript** queda SOLO lo realtime: los `moveC` (círculos) + leer entradas de sensores +
  capturar `get_actual_tcp_pose()` en cada flanco. Devuelve poses crudas a Java; **Java hace todo
  el cálculo**.
- Nuevo package `tcpcalibration` con view/contribution/service; las mates en clase aparte
  **`TCPCalibrationMaths.java`** (Java puro, sin dependencia de UR API → portable a Estun).
- **Envío del URScript:** interfaz **Secondary (puerto 30002)** — confirmado en el decompilado de
  CAPTRON (`.../de/captron/urcap/A/A/A.java`: `Socket("127.0.0.1", 30002)` + `println(script)`).
  Dashboard (29999) NO sirve (solo carga/play .urp). La precisión de 2 ms la da el thread+sync
  corriendo EN el controlador, no el canal de envío.
- **Orientación (RX/RY):** probar primero SUBIENDO (círculo a Z mayor, +Z del marco **base** = la
  antorcha sube); si no cruza el haz, probar más BAJO (−Z); si tampoco cruza → error
  `ORIENTATION_NOT_POSSIBLE`. Δz configurable (def ±5 mm = `params.offsetZMm`).

Decidido con el usuario: nuevo package **EN PARALELO** (no se toca lo viejo; al final se decide si
se borra); nodo de programa nuevo + `TCPCalibrationMaths` compartido con la instalación.

---

## 4. Estado del código — qué está HECHO

### Núcleo matemático (Fase 1) ✅
- **`tcpcalibration/TCPCalibrationMaths.java`** — Java PURO, sin UR API (reutilizable Estun).
  Poses `double[6]={x,y,z,rx,ry,rz}` (m + rotvec rad, convención UR). Transformadas homogéneas
  propias (`rotVecToMatrix`/`matrixToRotVec` Rodrigues π-safe, `poseTrans`, `poseInv`,
  `homInverse`); mates portadas y verificadas: `midpoint`, `intersect2D`, `computeCenter`
  (CenterResult OK/-1/-2/-3), `meanDiameter`, `correction` = `poseInv(poseTrans(poseInv(pRef),
  pSearchZ))`, `correctedTcp`, `calcAngleXY` + `correctAngle`, `withinTol`.
- **`TCPCalibrationMathsTest.java`** — 12 tests JUnit5, **todos verdes**.

### URScript mínimo (Fase 2) ✅
- **`src/main/resources/scripts/tcpcalib.script`** — defs `tcpc__*`: SOLO `moveC` + hilo de flancos
  (`get_actual_tcp_pose` en cada transición) + búsqueda Z. SIN matemática. `tcpc__runCircle`
  devuelve por socket `"C;count1;count2;<8 poses>"`; `tcpc__runSearchZ` devuelve `"Z;<pose>"` /
  `"Z;FAIL"`. Cada pose es `"x,y,z,rx,ry,rz"`.

### Comunicación + orquestación (Fase 3) ✅
- **`util/comms/SecondaryScriptSender.java`** — envía al puerto **30002** (como CAPTRON).
- **`tcpcalibration/TCPCalibrationRunner.java`** — abre `ServerSocket` en **5511**, manda
  `lib + call` por secondary, parsea las poses crudas y llama a `TCPCalibrationMaths` para TODO el
  cálculo (computeCenter → searchZ → correction → orientación opcional).
- **`TCPCalibrationSpec.java`** (entrada) / **`TCPCalibrationResult.java`** (salida, status
  OK / NO_ROBOT_REPLY / NO_INTERSECT / SEARCH_Z_FAILED / OUT_OF_TOLERANCE /
  ORIENTATION_NOT_POSSIBLE).

### Nodo de programa nuevo (Fase 4) ✅
- **`TCPCalibrationService` / `Contribution` / `View`** — id `com.GIA.GIATcp.tcpcalibration`,
  **título "GIA TCP"** (no "Action" como CAPTRON).
- La View (localizada ES/EN) expone: selección de TCP de la instalación, check **"Corregir también
  la orientación (RX/RY)"**, **tolerancias ± por eje X/Y/Z** (def 1.0 mm), botón **Calibrate**.
- El botón corre el runner en vivo (off-EDT), guarda el TCP corregido; `generateScript` emite
  `set_tcp(corregido)`. Todos los setters envueltos en `recordChanges` (UndoableChanges).
- Los parámetros geométricos (radio, velocidad, búsqueda Z) siguen en la **instalación**.

### Nodo viejo DESACTIVADO (no borrado) ✅
- `ProgTcpAction` (+ su hijo `ProgErrHandling`) **desactivado**: registro comentado en
  `Activator.start` (código intacto; reactivar = descomentar las dos líneas). Así solo aparece UN
  "GIA TCP" en el menú de insertar (el nuevo).

### Infra ya existente (fases previas)
- **i18n ES/EN** replicando GIAWeld: package `locale` (`Utf8Control` + `Texts`), bundles
  `localizations/gia-tcp.properties` (EN, Locale.ROOT) y `gia-tcp_es.properties` (ES). Locale del
  pendant vía `getSystemAPI().getSystemSettings().getLocalization()`.
- **Pantalla Diagnostics** (read-only): IO en vivo + pose TCP realtime (:30003), log de flancos.
- **Test de repetibilidad**: sondeo XYZ N veces (measure-only), agrega media/σ/rango por eje.
- **Verificación matemática** para el jefe: `docs/verificacion_matematica.md` (7 puntos, sin
  emojis). Conclusión: **NO hace falta matemático**; solo bring-up y tuning en hardware.

**Build:** `mvn -o clean package install -Pursim` · ursim.home =
`/opt/ursim-proves/ursim-5.22.0.1214828` · jar → `${ursim}/.urcaps/com.GIA.GIATcp.jar`.
Estado: **BUILD SUCCESS (40 fuentes), 12 tests verdes, jar desplegado (jun 16)**.

---

## 5. LOS DOS modelos — CONFIRMADO e IMPLEMENTADO (2026-06-17) ✅

Se lanza **desde el botón "Calibrar test" de la instalación** (en vivo) Y **en runtime desde el nodo**.

- **Modelo 1 — En vivo (HECHO):** botón "Calibrar test" en el Overview de la instalación →
  `InstallationContribution.runTestCalibration` → `TCPCalibrationRunner` por secondary (30002) →
  muestra OK/Error + el TCP corregido y guarda la corrección en el store.
- **Modelo 2 — Runtime (HECHO):** el nodo calibra dentro del programa en ejecución vía el
  **`CalibrationServer`** (puerto 5512, arrancado en `Activator`, vivo mientras PolyScope está arriba).
  `generateScript` emite `tcpc__rtCalib(...)`: el robot hace los `moveC` + captura poses, las manda al
  servidor Java por una conexión persistente, el servidor hace toda la geometría con
  `TCPCalibrationMaths` y devuelve el TCP corregido, que el robot aplica con `set_tcp` / asigna a
  variable. Reutiliza el mismo math + `tcpcalib.script`.

**Arquitectura compartida:** la secuencia `calibrate()` vive en un solo sitio detrás de
`ProbeTransport`; `SecondaryProbeTransport` (live) y `ServerProbeTransport` (runtime) solo cambian el
transporte. Toda la geometría sigue en `TCPCalibrationMaths` (Java puro, reusable Estun).

**Paridad completa en el nodo "GIA TCP"** (nodo nuevo, stack limpio): acciones Check/Validate/
Recalibrate, pestañas Básico/Tolerancias/Asignación, asignación a variable, manejo de errores con
hijo **If-Error** (reutiliza `ProgErrHandlingService`). El nodo viejo `ProgTcpAction` sigue desactivado.

**Pendientes menores (no bloqueantes):** Check re-mide completo (no el immerse-only ligero de
CAPTRON); la tolerancia de diámetro no se aplica en el stack nuevo (`withinTol` solo XYZ).

### Gaps históricos respecto al nodo viejo (ya cubiertos salvo lo de arriba)
El nodo nuevo cubre: selección de TCP, calibración XYZ (≈ Recalibrate), corrección de orientación,
tolerancias X/Y/Z. Le **falta**: acción TCP Check, acción TCP Validate, asignación a variable de
programa, bloque If-Error, tolerancia de diámetro. La diferencia de fondo es el **modelo de
ejecución** (nuevo = en vivo; viejo = runtime con todas las acciones).

---

## 6. Pendiente de validación en hardware real

El simulador NO dispara las barreras de luz → 0 éxitos es lo esperado en sim. Sin probar:
teach center, referencing/calibración real, cableado IO, polaridad dark-operate. Checklist en el
README del proyecto.

---

## 7. Restricciones de trabajo (vigentes)

- **NO** modificar el núcleo matemático de GIAWeld (`Maths`/`SeamTracking`/poses sincronizadas) sin
  permiso explícito. El math nuevo de giatcp es código propio → OK tocarlo.
- giatcp **no está bajo git** → los borrados no se recuperan.
- Idioma del usuario: **español**. Textos para el jefe: serios, sin emojis, sin la palabra "jefe",
  escritos como si los hubiera escrito el usuario.
- El usuario prefiere aclaraciones conversacionales antes que cuestionarios.

### Reglas técnicas aprendidas (no obvias)
- **`setBorder()` sobre el panel raíz de un nodo de programa** → `AuthorizationException`. Meter
  todo en un `JPanel content` interior con el borde. `setLayout()` sí se permite.
- **Toda escritura al DataModel de un nodo de programa** debe ir dentro de
  `undoRedoManager.recordChanges(new UndoableChanges(){...})` o salta `IllegalStateException`. Los
  callbacks del teclado numérico ya corren en scope UR; los listeners Swing NO.
