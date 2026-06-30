# GIA TCP Calibrator — Wiki del proyecto

> URCap de GIA ROBOTICS para calibrar el TCP (punta de la antorcha/hilo) de un robot
> Universal Robots usando barreras de luz SensoPart. Documento de referencia, fácil de leer,
> separado por secciones.

---

## 1. Introducción

El **TCP** (Tool Center Point) es el punto exacto de la herramienta que el robot considera
"su punta" (en soldadura, la punta del hilo/antorcha). Si ese punto está mal definido, el
robot suelda desplazado: el cordón se va de sitio. Cada vez que se cambia la boquilla, se
choca, o se desgasta la herramienta, el TCP se desajusta y hay que **recalibrarlo**.

**GIA TCP Calibrator** es una **URCap** (una app que se instala dentro del PolyScope del robot)
que automatiza esa calibración:

- Mide la posición real de la punta con un sensor óptico.
- Calcula la corrección (cuánto se ha movido el TCP en X, Y, Z y opcionalmente ángulo).
- Permite, dentro de un programa de soldadura, **comprobar / validar / recalibrar** el TCP
  automáticamente antes de una serie.

Es un producto **propio de GIA**, de marca GIA, sin depender de licencias externas. Nace como
una reimplementación del flujo de trabajo clásico de los calibradores de TCP por barrera de luz,
pero con código y nombre GIA.

**Hardware definitivo:** 2 × **SensoPart FGL 50-IK-50-PS-M4** (ref. 832-11022), barreras de
horquilla infrarrojas, montadas en cruz. Se integra en la célula *Estación_Cobot_HEM*
(cobot UR sobre eje lineal FESTO de 6 m, antorcha KEMPPI, estación doble de soldadura).

---

## 2. Idea

### 2.1 El principio físico
Una **barrera de luz de horquilla** es un emisor y un receptor enfrentados: cuando algo se mete
en medio y corta el haz, la salida del sensor cambia de estado. Es un detector de "hay / no hay
objeto en el haz", con un flanco muy preciso.

Con **una sola barrera** solo puedes medir bien el eje perpendicular al haz (+ la altura Z).
Para medir **X e Y** del punto necesitas **dos haces cruzados**. Por eso se usan **dos sensores
FGL 50 a 90°**: forman una cruz, y la punta entra vertical por el centro de la cruz.

### 2.2 Cómo se obtiene el centro (la matemática, en simple)
1. El robot lleva la punta cerca del cruce de los haces.
2. Hace un **movimiento circular** pequeño (Ø ~30 mm) alrededor de ese punto.
3. Mientras gira, la punta **corta cada haz dos veces**. Cada corte/recuperación es un **flanco**
   de la entrada digital; en ese instante se guarda la pose del robot.
4. Con los puntos de cada barrera se traza una **línea**; con las dos líneas se calcula su
   **intersección** = centro real de la herramienta en XY.
5. La diferencia entre ese centro y donde el robot "creía" tenerlo = **corrección X/Y**.
6. Para **Z**, baja la punta por el centro hasta que los haces dejan de estar cortados
   (búsqueda de Z). El **diámetro** de la herramienta sale de la longitud de las cuerdas.

### 2.3 Por qué dos sensores y no el calibrador CAPTRON original
El proyecto arrancó clonando el flujo de un calibrador comercial (CAPTRON), pero:
- No se podía usar su licencia (era para su URCap, no para uno de GIA).
- Se decidió hardware propio: las **SensoPart FGL 50**.
Como el FGL 50 es de **un solo haz**, se confirmó usar **dos cruzados**, lo que encaja
perfectamente con el algoritmo de dos barreras → cero reescritura del núcleo matemático.

### 2.4 Lo difícil de verdad
No es la matemática (intersección de dos rectas es trivial). Lo difícil es la **robustez en
robot real**: capturar los flancos con precisión (limitado por el bucle de control del UR a
500 Hz, no por el sensor que va a 2 kHz), velocidades de sondeo seguras, evitar colisiones en la
inmersión Z, y **repetibilidad**. Por eso hay una pantalla de diagnóstico y un test de
repetibilidad: para validar el hardware antes de fiarse de los números.

---

## 3. Flujo de work

### 3.1 Puesta en marcha (una vez, con el sensor montado)
1. **Montaje** (Pronimetal): dos FGL 50 en cruz, haces coplanarios y ~perpendiculares, hueco
   para el círculo de sondeo y para la inmersión Z. Detalle en
   `docs/montaje_calibrador_2sensores.md`.
2. **Cableado**: cada salida PNP → una entrada digital del UR; ambos sensores en **dark-operate**
   (haz cortado → 24 V); filtro de entrada del UR al mínimo.
3. **Validación con Diagnostics**: en *Installation → GIA TCP Calibrator → Diagnostics*, meter la
   punta en cada haz y comprobar que el indicador de esa entrada se pone **verde/HIGH**. Si sale
   invertido, cambiar el N.O./N.C. del sensor.

### 3.2 Configurar un TCP (asistente de 7 pasos)
En *Installation → GIA TCP Calibrator*, botón **+**, se abre el **Setup Wizard**:
1. **Variante de sensor** → SensoPart FGL 50-IK (ya viene por defecto).
2. **Entradas IO X / IO Y** → las dos entradas donde están cableados los sensores.
3. **TCP de referencia** → el TCP base respecto al que se mide la corrección.
4. **Enseñar el centro** → se mueve el robot hasta tener ambos haces cortados y se pulsa
   *Set Center*.
5. **Parámetros** → radio de sondeo, velocidad, aceleración, búsqueda Z, etc.
6. **Referencing** → primera medición real; el robot sondea y reporta la corrección.
7. **Hecho**.

### 3.3 Calibrar / medir (instalación)
- **Calibrate** (Overview): lanza una calibración y guarda la corrección. La badge pasa a
  **CALIBRATED** y se muestra X/Y/Z + RX/RY/RZ.
- **Diagnostics → Repeatability test**: repite el sondeo N veces **sin** cambiar el TCP, y
  reporta **media, sigma (σ) y rango** por eje + diámetro. Es el **test de aceptación**: σ
  pequeña (objetivo < 0,1 mm) = sistema repetible.

### 3.4 Usar dentro de un programa de soldadura
En el árbol del programa se inserta el nodo **GIA TCP Action**:
- **TCP Check**: comprueba que la punta sigue donde debe (rápido, sin recalibrar).
- **TCP Validate**: mide y verifica que la desviación está dentro de tolerancias (no cambia el TCP).
- **TCP Recalibrate**: mide y **aplica** la corrección; escribe el resultado en la variable
  `giaActionTCP` (o una propia); opcionalmente hace `set_tcp` tras recalibrar.
- Si está activado el **manejo de errores**, cuelga un nodo hijo **If Error** que se ejecuta si
  la acción falla (estado ≠ 0).

### 3.5 Cómo viaja la información (comunicaciones)
```
  Instalación (calibrar / repetibilidad):
    URCap  --programa URScript-->  Primary interface :30001 (el robot se mueve)
    Robot  --resultado por socket--> URCap :5510 (centro, diámetro, estado)

  Diagnostics (solo lectura, no mueve el robot):
    IO en vivo   <- IO API DigitalIO.getValue()
    Pose en vivo <- Realtime interface :30003 (RobotRealtimeReader)

  Nodo de programa (GIA TCP Action):
    URCap genera el URScript con generateScript() -> entra en el programa del usuario
```

---

## 4. Código y scripts importantes

Estructura: `src/main/java/com/GIA/GIATcp/...` (Java de la URCap) +
`src/main/resources/scripts/*.script` (algoritmo en URScript).

### 4.1 URScript (el algoritmo, en el robot)
Se cargan en orden y se sustituyen los `{{tokens}}` (ver `ScriptResourceLoader` / `ScriptLibrary`).

| Archivo | Qué hace |
|---|---|
| `gl.script` | Funciones globales: lectura de entradas (`gia__getInput`), cálculo de puntos del círculo, **intersección 2D de dos rectas** (`gia__calc2DIntersect`), ángulos, utilidades, reporte de resultado por socket (`gia__reportResult`). |
| `interrupt_points.script` | **Captura de flancos**: un hilo (`gia__interruptThread`) lee las dos entradas a frecuencia de control; cada cambio HIGH/LOW guarda `get_actual_tcp_pose()`. `gia__interruptPoints` hace el círculo y recoge 4 puntos por barrera. **Convención clave: entrada HIGH = haz cortado.** |
| `search_z.script` | Búsqueda e inmersión en Z: baja la punta hasta que los haces se liberan/cortan para hallar la altura. |
| `adjust_angle.script` | Ajuste opcional de ángulo RX/RY (lo más fino; dejar desactivado hasta validar XYZ). |
| `inst.script` | **API pública** `gia_tcp_*`: `calibXYZ` (calibración completa XYZ), `calibAngleXY`, `checkCalib`, `immerseZ`, `initCalib`, `isActionOk`, `getStatus`/`getStatusMsg`, `getCalibCorrection`, `getDiameterMM`, `activateTCP`/`setTCP`, `setStored`. Mantiene el estado por TCP (hasta 30) en arrays. Códigos de error 0 / −1…−7 / −11…−14 / −21 / −31. |

### 4.2 Java — Instalación
| Archivo | Qué hace |
|---|---|
| `installation/InstallationContribution.java` | Cerebro de la instalación: modelo de datos (30 TCPs), `generateScript` (mete la librería + calibraciones guardadas), lectura de IO/pose en vivo, teach/move, lanza calibración y repetibilidad. |
| `installation/InstallationView.java` | Contenedor de tarjetas (Overview / Wizard / Settings / **Diagnostics**); navegación y parada del monitor. |
| `installation/overview/OverviewCard.java` | Pantalla principal: seleccionar/añadir/borrar/renombrar TCP, lectura de corrección, Calibrate/Stop, botón Diagnostics. |
| `installation/wizard/SetupWizard.java` | Asistente de 7 pasos. |
| `installation/diagnostics/DiagnosticsCard.java` | **Diagnóstico en vivo** (read-only): indicadores de IO, pose en vivo, log de flancos, Move-to-center, **Repeatability test**. |
| `installation/calib/CalibrationController.java` | Construye y envía el programa de calibración (primary :30001) y lee el resultado (socket :5510). |
| `installation/measure/RepeatabilityController.java` + `RepeatabilityResult.java` | Test de repetibilidad: N sondeos en un solo programa, agrega **media/σ/rango**, sin tocar el TCP. |
| `installation/model/` | `GiaTcp` (un TCP), `TcpStore` (persistencia en DataModel), `TcpVariant` (**solo SensoPart FGL 50-IK**), `CalibParams`, `IoOption`. |

### 4.3 Java — Comunicaciones
| Archivo | Qué hace |
|---|---|
| `util/comms/PrimaryScriptSender.java` | Envía un programa URScript completo al interface primary (:30001) para mover el robot ya. |
| `util/comms/RobotRealtimeReader.java` | Lee la **pose TCP en vivo** del interface realtime (:30003), sin intrusión (no ejecuta nada). |

### 4.4 Java — Nodo de programa
| Archivo | Qué hace |
|---|---|
| `program/action/ProgTcpActionContribution.java` / `View` / `Service` | Nodo **GIA TCP Action**: Check/Validate/Recalibrate, tabs, tolerancias, asignación de variable, toggle de error handling. |
| `program/errhandling/*` | Nodo hijo **If Error** que se ejecuta si la acción falla. |

### 4.5 Detalle clave para hardware: polaridad
En `interrupt_points.script` el algoritmo **espera que la entrada esté en LOW con la punta fuera**
y registra el punto al pasar a **HIGH**. → los dos FGL 50 deben ir en **dark-operate** (24 V cuando
el haz está cortado). La pantalla **Diagnostics** sirve para verificarlo de un vistazo.

---

## 5. Idea final

### 5.1 Estado actual
- URCap **compila y se instala** en el simulador (`GIA-TCP-1.0.urcap`).
- Funciona en software: gestión de TCPs, wizard, generación de script, nodo de programa,
  **pantalla de diagnóstico en vivo** y **test de repetibilidad**.
- Branding 100 % GIA en la UI; hardware fijado a 2 × SensoPart FGL 50.
- Spec de montaje para Pronimetal escrita y lista para pasar.

### 5.2 Lo que falta (necesita robot + sensores reales)
El simulador **no dispara las barreras**, así que lo siguiente solo se valida en hardware:
1. Montar los dos sensores en cruz según la spec y cablearlos.
2. Verificar polaridad y cableado con **Diagnostics** (haz cortado → verde).
3. Enseñar centro y lanzar **Referencing**.
4. **Medir repetibilidad** (objetivo σ < 0,1 mm); bajar velocidad de sondeo si hace falta.
5. **Repetibilidad ≠ exactitud**: validar la exactitud contra una referencia independiente
   (TCP patrón / pin-in-hole).
6. Solo entonces, si se quiere, activar el **ajuste de ángulo RX/RY** (lo más delicado).

### 5.3 Filosofía
Se ha construido **por fases** y de forma honesta: nada de "terminarlo de golpe y confiar a
ciegas". El código generado (sobre todo el algoritmo de sondeo y la intersección) es **plausible
pero no validado en hardware**; se trata como borrador hasta que el robot diga lo contrario. Las
dos herramientas de diagnóstico (IO/pose en vivo y repetibilidad) existen precisamente para
**medir la realidad** antes de fiarse de la calibración.

### 5.4 Resumen en una frase
> Una URCap propia de GIA que, con dos barreras SensoPart cruzadas, mide la punta de la antorcha
> por sondeo circular + intersección de dos líneas, lista en software y a la espera de validación
> en robot real, con diagnóstico y repetibilidad integrados para hacer esa validación con datos.
