# GIA TCP Calibrator — Contexto y estado actual

> Documento de contexto/handoff. Resume lo importante del proyecto y el punto exacto en el
> que estamos. Última actualización: 2026-07-02.

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
- **`tcpcalibration/TCPCalibrationRunner.java`** — secuencia común del stack nuevo. En live usa
  `SecondaryProbeTransport` con retorno **5511**; en runtime usa `ServerProbeTransport` contra el
  servidor **5512**. Parsea poses crudas y llama a `TCPCalibrationMaths` para TODO el cálculo
  (computeCenter → searchZ → correction → orientación opcional).
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
Estado histórico: **BUILD SUCCESS**; validar de nuevo tras cada cambio con `mvn test`.

---

## 5. Estado real de modelos y Z (actualizado 2026-06-30)

Hay **dos caminos nuevos Java** y **dos caminos legacy aún activos**:

- **Live nuevo:** botón **"Calibrar (test)"** en Overview →
  `InstallationContribution.runTestCalibration` → `TCPCalibrationRunner` + `SecondaryProbeTransport`
  por secondary **30002** → retorno **5511** → Java calcula y guarda la corrección.
- **Runtime nuevo:** nodo **"GIA TCP"** → `generateScript` emite `tcpc__rtCalib(...)` → robot mueve y
  captura poses → `CalibrationServer` en **5512** calcula con `TCPCalibrationMaths` → devuelve TCP
  corregido para asignar/aplicar.
- **Legacy activo:** botón **"Iniciar referenciado"** del wizard → `CalibrationController` → primary
  **30001** + scripts `gia_tcp_*` → retorno **5510**.
- **Legacy activo:** Diagnostics → Repeatability test → `RepeatabilityController` → primary **30001** +
  scripts `gia_tcp_*` → retorno **5510**.

**Arquitectura objetivo:** el cálculo portable vive en `TCPCalibrationMaths` y la secuencia común en
`TCPCalibrationRunner` detrás de `ProbeTransport`. Los caminos legacy se mantienen porque aún dan
servicio al wizard/repetibilidad, pero no son el modelo objetivo para Estun.

**Z corregida:** los campos de UI son magnitudes positivas. `CalibParams` calcula el signo:
`signedApproachZMm()` va por el lado de retracción segura, `signedSearchZMm()` define la retracción
de búsqueda y `signedImmerseZMm()` vuelve en sentido contrario para recortar los haces. Se replica la
convención CAPTRON: default `invertZ=false`; con montaje top-down y `+Z` de herramienta hacia abajo,
Approach/Search van en `-toolZ` e Immerse va en `+toolZ`. Activar `invertZ` invierte esos sentidos.

**Nodo "GIA TCP" activo:** Check/Validate/Recalibrate, pestañas Básico/Tolerancias/Asignación,
asignación a variable, manejo de errores con hijo **If-Error**. El nodo viejo `ProgTcpAction` sigue
desactivado en `Activator`.

### Endurecimiento del flujo live + diámetro (2026-07-02, paridad CAPTRON verificada contra el decompilado)

- **`set_tcp(refTcp)` en cada programa live inyectado** (`SecondaryProbeTransport`): antes las poses
  se capturaban con el TCP que estuviera activo → corrección silenciosamente desplazada. CAPTRON
  antepone `set_tcp` a todos sus programas (factoría `D.A` del decompilado).
- **"Calibrar (test)" ahora es guiado** (Overview y botón del nodo): check de Remote Control (el
  botón del nodo no lo tenía) → `set_tcp(ref)` por secondary → pantalla de mover-robot al centro
  enseñado → al llegar, sondeo. Sin movel ciego desde parking. Si el usuario cancela la pantalla de
  movimiento no hay callback (API UR sin evento de cancelación), por eso el botón se rehabilita
  ANTES de abrirla.
- **`Fijar centro` valida el TCP activo**: rechaza la pose si no se enseñó con el TCP de referencia
  (CAPTRON: "Can't teach center pose, wrong TCP"). Clave `WIZ_CENTER_WRONG_TCP`.
- **Repetibilidad** comprueba Remote Control antes de inyectar (fail-fast en Local, como el resto).
- **Diámetro (semántica CAPTRON):** el referenciado guarda la medida CRUDA (diamOffset 0 en
  `CalibrationController`/`RepeatabilityController`; antes pasaban `realDiameterMm` como offset, lo
  que sumaba la boquilla entera al medido). En runtime el INIT manda `diamOff = real − referenciado`
  (0 si falta alguno o si es referenciado) + banda de tolerancia Ø: campos opcionales 15/16 del INIT
  (`tolDmm;diamNomMm`), fila Ø en la pestaña Tolerancias (`K_TOL` "D", default
  `DEF_TOL_DIAM_MM = 2.0`), check en `TCPCalibrationRunner` con `withinTolVal` → OUT_OF_TOLERANCE.
  Nominal = diámetro real configurado, o el referenciado si no hay real. También corregido en el
  nodo legacy desactivado.
- **Check/Validate restauran el TCP del programa** (`giaTcpBak = get_tcp_offset()` → acción →
  `set_tcp(giaTcpBak)` al final, tras el hijo If-Error): antes el nodo dejaba el TCP de referencia
  activo y el resto del programa seguía con un TCP cambiado en silencio. Recalibrate no restaura,
  igual que CAPTRON (su objetivo es continuar con el TCP corregido).
- **Recorrido recortado (queja del usuario: "se expande demasiado")**: defaults redimensionados
  para sondear la PUNTA DEL HILO — radio 10→**6 mm** (Ø12), Search Z 12→**8**, Approach Z 50→**30**;
  overrun se queda en 10° (debe superar el semiarco bloqueado en el arranque: ~10° con hilo y radio 6).
  Regla física del radio mínimo (documentada en Const/README/montaje): ambos haces deben quedar
  LIBRES a la vez para armar la captura → radio > (radio útil + medio haz + margen)/sen 45°; con
  BOQUILLA Ø16–20 hacen falta ≥13–16 mm (por eso NO se puede bajar el radio si se sondea boquilla).
  Además, eliminado el `movel(pStart)` de vuelta al centro tras el círculo (herencia CAPTRON
  redundante: la búsqueda Z ya va por su cuenta al centro calculado). OJO: los TCPs y nodos ya
  guardados conservan sus valores; los defaults nuevos solo aplican a TCPs/nodos nuevos.

### Referencia de pose medida — CAPTRON h()/j() (2026-07-03)

- **El referenciado guarda la pose MEDIDA (`pSearchZ`) como `GiaTcp.refPose`** (clave `K_REFPOSE`,
  resultado ampliado con `measuredPose`, sink/`setCalibrationResult` con 4º parámetro; el camino
  legacy del wizard la reconstruye con `pRef · inv(traslación de la corrección)`).
- **El runtime corrige contra `refPose`, no contra el centro enseñado**: `correctionRefPose()` =
  refPose si `calibrated && hasRefPose()`, si no centerPose (compat con instalaciones viejas y
  primer referenciado). Es el `h()` de CAPTRON: tras el primer referenciado, el error humano del
  teach desaparece del lazo de medida.
- **El círculo sigue arrancando en el centro enseñado** (`TCPCalibrationSpec.pStart`, el `j()` de
  CAPTRON; fallback a pRef si falta). El runner separa ambos: sondeo/`computeCenter` con pStart,
  corrección con pRef. INIT amplía cola opcional: campo 17 = `pStartCsv`.
- **Re-enseñar el centro invalida la referencia** (`teachCenter` → `calibrated=false`, refPose a
  cero), como CAPTRON al fijar `j()`: obliga a re-referenciar.
- La corrección mostrada en Overview pasa a significar "deriva desde el referenciado".

### Revisión "entorno real" contra el manual oficial CAPTRON 1.3.0 (2026-07-03)

Manual: https://www.captron.com/fileadmin/user_upload/data/landingpage/Software_UR/Reference_Manual_URCap_CAPTRON_TCP_en_1.3.0_1.pdf
Confirmaciones clave del manual: el centro se enseña con AMBOS haces cortados y solo **1–2 mm**
de inmersión (troubleshooting nº 9); el overrun se ajusta si el círculo termina dentro de un haz;
"exactly 2 times" por barrera (nº 2, remedio: subir velocidad); tolerancias min/max (no ±);
error handling con "Try again x times"; el nodo tiene Move Start/Move Approach.

Arreglos aplicados:
- **Stop del Overview no paraba el test live**: solo llamaba al stop del stack legacy (30001).
  Ahora también manda un programa `def` con `stopl(2.0)` por secondary (preempta el programa de
  sondeo, como el Stop de CAPTRON) + línea "STOP" al puerto 5511 para desbloquear el read de
  `SecondaryProbeTransport` al instante (`sendStop`). Listener en hilo propio (socket I/O).
- **El test re-baseaba la referencia en cada clic**: `runTestCalibration` solo escribe `refPose`
  si no existe (bootstrap tras enseñar centro); corrección+diámetro se guardan siempre (como el
  Calibrate manual de CAPTRON, que guarda diámetro/corrección pero solo el wizard fija h()).
  Sin esto, la deriva medida se auto-borraba: dos tests seguidos siempre daban ~0.
- **`CircleData.valid()` endurecido a exactamente 4 flancos por haz** (antes aceptaba 6/8 pares:
  chatter del sensor emparejaba flancos de rebote y desplazaba el centro EN SILENCIO).
- **Guard CAPTRON "input low on intersect position"** en `tcpc__searchZMsg`: al llegar al centro
  calculado ambos haces deben estar cortados; si no (teach a profundidad errónea, herramienta
  rota, polaridad), aborta con Z;FAIL en vez de buscar desde un punto sin sentido.
- **El nodo se retira a la altura de aproximación al acabar la acción** (ok o fallo), como el
  move-back del nodo CAPTRON: el programa nunca continúa su trayectoria desde dentro de la
  horquilla (`movel(giaTcpApproach)` tras `tcpc__rtCalib`, antes del set_tcp de restauración).
- Docs: guía oficial de teach 1–2 mm añadida a montaje/WIKI/README.

### Recolocación final de la punta (2026-07-03)

- **Al terminar la búsqueda Z, la punta se recoloca entre los haces** (`tcpc__searchZMsg`):
  la retracción captura la pose del flanco de LIBERACIÓN (ambos haces libres) y, tras el flanco
  de corte de la inmersión, un `movel(interpolate_pose(corte, liberación, 0.5))` sube la punta
  al punto medio entre ambos flancos. Antes la secuencia se quedaba parada en el flanco de corte
  (punta clavada en el haz inferior). CAPTRON logra lo mismo con la recolocación final de su flujo
  live (`set_tcp(nuevo)` + `movel(poseRef)`, `tcp/inst/A.java:560`); el punto medio además cubre
  horquillas no coplanarias. La pose MEDIDA que se devuelve a Java sigue siendo el flanco de corte:
  la corrección Z no cambia. Aplica a test live y al nodo runtime (misma primitiva).

### 2026-07-06 — Prioridad alta de MEJORAS_PENDIENTES (items 1-5 + 10)

- **Check ligero (paridad CAPTRON `cap_immerseZ`/`cap_checkCalib`).** El nodo Check ya NO sondea:
  nueva primitiva `tcpc__lightCheck(pRef, in1, in2, zImmerse, acc, vel)` en `tcpcalib.script` —
  va a la pose referenciada, verifica ambos haces cortados y, si no, inmersión lenta (0.2×) de
  hasta Immerse Z para perdonar desgaste mínimo; sin servidor 5512 y sin círculo. Validate hace
  el light check ANTES del sondeo completo (falla rápido con estado preciso, como CAPTRON);
  Recalibrate no lo hace (la herramienta puede estar lejos tras el cambio de boquilla).
- **Bucle de reintento del nodo ("Try again x times").** `generateScript` envuelve la acción en
  `while (not giaTcpOk)`: cada iteración arranca del punto de aproximación y termina retirándose
  a él. El hijo If-Error corre CADA iteración (también en éxito, para resetear `giaTcpErrCount`)
  y decide entre reintento silencioso y ejecutar los hijos de recuperación: radio "inmediato" vs
  "reintentar N veces" en su vista (claves `errRetryEnabled`/`errRetryCount`, default 2 como
  CAPTRON). Sin error handling: un intento y `break`. OJO paridad CAPTRON: con error handling
  activado el bucle repite hasta OK — la recuperación del usuario debe corregir la causa o parar
  el programa (documentado en la vista).
- **Estados de error finos.** `Status` ampliado (ordinales son protocolo — solo añadir al final):
  `INPUT_LOW_AT_CENTER` (6, CAPTRON 4/21: no corta ambos haces en la referencia) e
  `IMMERSE_FAILED` (7, CAPTRON 31). `tcpc__searchZMsg` devuelve `Z;FAIL;LOW|SEARCH|IMMERSE`;
  `CalibCsv.parseZ` → nuevo `ZSearchResult` (pose o motivo tipado); `ProbeTransport.searchZ`
  cambió de firma. Textos ES/EN nuevos (`TC_ST_INPUT_LOW`, `TC_ST_IMMERSE`).
- **Gate de referenciado (CAPTRON `isDefined` con `c.b()`).** El nodo queda amarillo
  (`TC_ISSUE_NOT_REFERENCED`) si el TCP no está referenciado, SALVO que sea una pasada de
  referenciado (persist + Validate/Recalibrate) — esa excepción es nuestra adaptación: en modo
  Local el nodo con persist ES el camino para referenciar (CAPTRON no la necesita porque su
  wizard referencia). Check con persist no cuenta (no mide). Guard equivalente en
  `generateScript` (popup + halt).
- **Stop en el test live del nodo.** Botón Stop junto a "Calibrar (test)" en
  `TCPCalibrationView`, mismo mecanismo que el del Overview (`stopTestCalibration`); si el
  usuario paró, el diálogo dice "Test detenido" en vez de "sin respuesta".
- **Bonus (item 10): aproximación rápida en dos tramos.** Primer tramo al punto de aproximación
  a `80·factor` mm/s² / `60·factor` mm/s (CAPTRON), tramo final a velocidad de sondeo. Anclaje
  del approach como CAPTRON: Check/Validate sobre la pose referenciada (h()), Recalibrate sobre
  el centro enseñado (j()).
- Tests: 35 verdes (7 nuevos: `CalibCsvZParseTest`, `TCPCalibrationRunnerZFailureTest`).

### 2026-07-07 — Prioridad media completa (items 6-13) + carpeta releases/

- **Validación del wizard (6):** clamp por parámetro con reescritura del campo (rangos en
  `Const`: radio 2-20, velocidad 5-100, acel 20-2000, overrun 5-45, search Z 3-50, Ø 0-25,
  iter 1-10, offZ 1-20, precisión 0.05-5) + aviso del radio mínimo físico
  (`Const.minRadiusForTool`: (Ø/2+1.5)/sen 45, hilo 1.2 si no hay Ø real).
- **Min/Max asimétricos (7):** claves `actTolMin%s`/`actTolMax%s` (default ±simétrico legacy
  → migración transparente); INIT +8 campos de cola `tolMinX..tolMaxD` (t[17..24], mm); el
  servidor los prefiere y sin ellos cae al simétrico. Runner: `withinTolAsym` y banda de Ø
  direccional (min/max ≠ 0 la activan; si no, el simétrico legacy).
- **Previous (8):** `CalibrationServer.recordResult/lastResultFor` (mapa estático por tcpId,
  [xMm,yMm,zMm,ØMm]) alimentado por sesiones runtime y `runTestCalibration`; la pestaña de
  tolerancias lo muestra por fila (Ø en la fila Ø). Se refresca al abrir la vista.
- **Move Start/Approach (9):** `actionStartPose()` (h()/j() según acción) y
  `actionApproachPose()` (± Approach Z por `signedApproachZMm`); pantalla guiada
  (`requestMove`); si `activateReferenceTcp` falla (Local), confirmación explícita porque la
  pantalla apunta con el TCP activo actual.
- **gia_* (11):** wrappers públicos en `tcpcalib.script` (ASCII only), incl.
  `gia_getStatusMsg()` bilingüe ES/EN.
- **UndoableChanges (12):** verificado y cerrado sin cambios (programa ya canónico;
  instalación no lo requiere).
- **Timeouts (13):** live accept 60 s/read 15 s; `tcpc__rtCalib` 6×10 s; servidor 5512
  120 s por lectura (el robot debe completar cada primitiva de movimiento dentro de eso).
- **releases/**: una `.urcap` por versión commiteada (v2 prioridad alta … v8 referenciado pasivo).
- Tests: 39 verdes (parseInit min/max ×2, runner asimétrico ×2, más los previos).
- **Referenciado pasivo en modo Local (entrada 20):** "Iniciar referenciado" en Local ya no da
  error: registra `ReferencingListener` en la contribución y espera a que un programa con nodo
  GIA TCP + "Guardar como referencia" (Play) persista por el sink 5512; al llegar el resultado
  del TCP esperado, el paso se marca completado (`WIZ_REF_PASSIVE_WAIT/DONE`). Parar cancela.
  Es la solución al robot del usuario (pendant 3PE sin Remote Control).

**Pendientes menores:** queda por decidir si migrar wizard y repetibilidad al stack nuevo; el
ajuste RX/RY del stack nuevo es single-shot (los parámetros `iterator`/`accuracyDeg` del wizard
solo los usa el stack legacy — la iteración con re-centrado queda para cuando se valide XYZ en
hardware). Resto en `docs/MEJORAS_PENDIENTES.md` (baja: 14-17; validación: 18-19; propuesta 20).

### 2026-07-13 — Auditoría externa verificada + fixes SEV-1 (bloque 1)

Auditoría externa (Codex) en `docs/AUDITORIA_TCP_FABLE5.md`, verificada hallazgo a hallazgo
contra el código y el decompilado CAPTRON antes de tocar nada. Veredicto: 4 SEV-1 son
desviaciones reales de CAPTRON (corregidas aquí), 2 son paridad CAPTRON exacta que el informe
marca como fallo (sleep de Z y diámetro por cuerda → items 29-30, mejoras opcionales), 1 es
física del montaje de 2 horquillas (descoplanaridad → refuerza el item 15).

Fixes aplicados (uno por commit):
- **Referenciado re-basa con corrección identidad.** El primer referenciado calculaba la
  corrección contra el centro enseñado j (error de teach + 1-2 mm de inmersión deliberada) y
  un persist+Recalibrate con defaults la APLICABA (`set_tcp(refTcp·q⁻¹·j)`) — justo el flujo
  Local de la entrada 20. CAPTRON guarda la pose medida como h() ANTES de calcular
  (`tcp/inst/A.java:523-526`) → corrección identidad. Nuevo `TCPCalibrationSpec.referenceRun`
  (persist ⇒ true; test live bootstrap ⇒ true), runner reporta identidad (el RX/RY medido se
  conserva), wizard legacy persiste corrección solo-rotación + refPose reconstruida.
  Instalaciones existentes: la corrección heredada ≠ 0 es solo display y se normaliza al
  re-referenciar (item 19).
- **RX/RY anclado en `pSearchZ`.** El círculo angular se anclaba en pRef: la deriva XYZ
  recién medida entraba en el atan2 (1 mm con Δz=5 mm = 11,3° falsos > máx 10° →
  ORIENTATION_NOT_POSSIBLE con herramienta recta). Ambas medidas comparten ahora el mismo
  error de TCP y su diferencia aísla la inclinación (equivale al set_tcp corregido de
  CAPTRON en `adjust_angle.script` sin ampliar el protocolo 5512).
- **tcpId materializado en openView** (el default dinámico `firstTcpId()` cambiaba el TCP
  del nodo en silencio al crear un slot inferior; CAPTRON usa ctId=-1 indefinido).
- **5510 endurecido**: bind a loopback (calibración y repetibilidad) y parse estricto de 8
  campos finitos — una línea truncada "0" contaba como referenciado OK con corrección cero.

Tests: **48 verdes** (nuevos: identidad en reference run, referenceRun en parseInit,
orientación ×3 —deriva sin falsa inclinación, ancla del círculo, inclinación real—, parse
5510 ×5). Pendientes de la auditoría: items 28-37 de MEJORAS_PENDIENTES (el 28, Check/
Validate con el TCP calibrado activo, requiere estudiar antes cómo persiste CAPTRON las
recalibraciones de runtime). Release `v10_fixes-auditoria-sev1`.

### 2026-07-10 — Nodo de programa: cabecera compacta

- El nodo cortaba la pestaña Básico a partir de "Ajuste de ángulo activo" (Iterador de
  precisión y Desfase Z no cabían). Causa: la cabecera consumía ~160 px (logo 153×68 +
  título + struts) antes de las pestañas.
- Quitado el `JLabel` "GIA TCP" del nodo (PolyScope ya pinta el título del nodo encima →
  salía duplicado). Logo reducido a `Ui.logoSmall()` (90×40, mismo aspecto 2.25:1) y pegado
  a la esquina superior derecha del panel raíz (fuera del `content` con borde). El selector
  de TCP comparte fila con el logo. Las tarjetas de instalación/wizard siguen con `Ui.logo()`.

---

## 6. Pendiente de validación en hardware real

El simulador NO dispara las barreras de luz → 0 éxitos es lo esperado en sim. Sin probar:
teach center, referencing/calibración real, cableado IO, polaridad dark-operate. Checklist en el
README del proyecto.

---

## 7. Restricciones de trabajo (vigentes)

- **NO** modificar el núcleo matemático de GIAWeld (`Maths`/`SeamTracking`/poses sincronizadas) sin
  permiso explícito. El math nuevo de giatcp es código propio → OK tocarlo.
- giatcp está bajo git (rama `Codex`) desde 2026-07: commitear versiones estables al cerrar cada bloque.
- Idioma del usuario: **español**. Textos para el jefe: serios, sin emojis, sin la palabra "jefe",
  escritos como si los hubiera escrito el usuario.
- El usuario prefiere aclaraciones conversacionales antes que cuestionarios.

### Reglas técnicas aprendidas (no obvias)
- **`setBorder()` sobre el panel raíz de un nodo de programa** → `AuthorizationException`. Meter
  todo en un `JPanel content` interior con el borde. `setLayout()` sí se permite.
- **Toda escritura al DataModel de un nodo de programa** debe ir dentro de
  `undoRedoManager.recordChanges(new UndoableChanges(){...})` o salta `IllegalStateException`. Los
  callbacks del teclado numérico ya corren en scope UR; los listeners Swing NO.
