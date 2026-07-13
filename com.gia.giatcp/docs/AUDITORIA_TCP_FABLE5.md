# Auditoría de arquitectura y lógica de cálculo del TCP

## URCap GIA TCP frente a CAPTRON TCP 1.3.0

Fecha de revisión: 2026-07-13

Las rutas de referencia CAPTRON fueron accesibles y se contrastaron directamente:

- Scripts originales: `/home/adnan/Documents/urcaptcp/CAPTRON-TCP-1.3.0_1.urcap/scripts/`.
- Decompilado CFR: `/home/adnan/Documents/urcaptcp/CAPTRON-TCP-1.3.0_1.decompiled/`.

No se repiten los hallazgos 21-27 de `docs/MEJORAS_PENDIENTES.md`, salvo cuando se demuestra un alcance materialmente mayor.

## Hallazgos SEV-1

### [SEV-1] El referenciado calcula la corrección contra `j()` o contra el `h()` anterior

**Dónde**: `GiaTcp.java:67-69`; `TCPCalibrationContribution.java:447-456`; `TCPCalibrationRunner.java:55-60`; `CalibrationServer.java:132-136,207-217`; `InstallationContribution.java:488-499,513-533`; stack legacy en `CalibrationController.java:91-107` e `InstallationContribution.java:448-465`.

**Qué falla**: en el primer referenciado, `correctionRefPose()` devuelve el centro enseñado `j`. Si la pose medida es `q`, se almacena:

```text
C = q^-1 * j
h = q
```

Con `j` enseñado 1,5 mm por debajo del plano, se guarda aproximadamente 1,5 mm de corrección Z aunque no exista deriva. En un re-referenciado se calcula `q_nuevo^-1 * h_antiguo` y después se sustituye `h` por `q_nuevo`, dejando corrección y referencia con bases distintas. Una pasada `persist + Recalibrate` puede aplicar inmediatamente ese TCP erróneo en `TCPCalibrationContribution.java:514-521`.

**Evidencia CAPTRON**: el wizard llama al flujo con `bl=true` en `tcp/inst/A/D.java:597-609`. CAPTRON captura `pSearchZ`, lo guarda primero como `h()` y solo después calcula la corrección en `tcp/inst/A.java:512-536`. La fórmula está en `A/A/D.java:169-176`. Por tanto, el XYZ del referenciado es identidad: `q^-1 * h = q^-1 * q = I`.

**Arreglo propuesto**: añadir un modo explícito `referenceRun`. Tras medir Z, usar `pSearchZ` como `pRef` para el XYZ, persistirlo como `h` y dejar identidad antes del ajuste angular opcional. Aplicarlo también al wizard legacy.

---

### [SEV-1] Check y Validate nunca activan el TCP calibrado almacenado

**Dónde**: `GiaTcp.java:25-29`; `TCPCalibrationContribution.java:402,447-456,466-480,488-509`.

**Qué falla**: la corrección almacenada no se usa para construir el TCP activo. Las tres acciones hacen `set_tcp(refTcp)`. Si el TCP calibrado es:

```text
A = R * C
```

con `C.x=1 mm`, Check lleva `R` a `h`, pero la punta física correspondiente a `A` queda desplazada aproximadamente 1 mm. Puede devolver `INPUT_LOW_AT_CENTER` aunque la calibración sea válida. Validate mide de nuevo la corrección absoluta desde `R`, no la deriva incremental desde `A`; una corrección almacenada de 1 mm permanece en el resultado aunque la herramienta no haya cambiado y se compara otra vez contra la tolerancia.

Además, `set_tcp` está fuera del `while`. Si un hijo If-Error cambia el TCP, el siguiente intento captura poses con otro TCP aunque Java siga usando `R` en `correctedTcp`.

**Evidencia CAPTRON**: `tcp/prog/tcp_action/C.java:351-359` selecciona `c.A()` para Check/Validate y `c.T()` para Recalibrate. Lo activa en `:193-197` y pasa esa misma base a `cap_calibXYZ` en `:253-264`.

**Arreglo propuesto**: para Check/Validate usar `A=poseTrans(refTcp,tcp.correction)` como TCP activo y como base enviada al runner. Para Recalibrate/referenciado usar `refTcp`. Reactivar el TCP previsto al inicio de cada intento.

---

### [SEV-1] RX/RY interpreta la traslación XYZ como inclinación

**Dónde**: `TCPCalibrationRunner.java:57-60,81-95,105-125`; `SecondaryProbeTransport.java:65-87`; runtime en `TCPCalibrationContribution.java:470,478-480`.

**Qué falla**: se calcula `correctedTcp`, pero el círculo angular se ejecuta todavía con `refTcp` y alrededor del antiguo `s.pRef`. Para una herramienta sin inclinación, con error X de 1 mm y `offsetZ=5 mm`:

```text
ángulo falso = atan2(1,5) = 11,3099 grados
```

Supera el máximo predeterminado de 10 grados y devuelve `ORIENTATION_NOT_POSSIBLE` para una herramienta recta. Con errores menores genera una corrección RX/RY falsa.

**Evidencia CAPTRON**: `scripts/inst.script:87-91` entrega el TCP ya corregido en XYZ a `cap__adjustAngleXY`; `scripts/adjust_angle.script:5-9` ejecuta `set_tcp(tcp)` antes de sondear. El flujo live hace lo mismo en `tcp/inst/A.java:536-560`.

**Arreglo propuesto**: activar `correctedTcp` antes del círculo angular. Una alternativa sin ampliar el protocolo 5512 es usar `pSearchZ` como ancla del segundo círculo y de `calcAngleXY`, eliminando el intercepto XYZ.

---

### [SEV-1] La descoplanaridad permitida deja hasta 0,53 mm de residuo XY tras corregir ángulo

**Dónde**: `TCPCalibrationMaths.java:123-136`; `TCPCalibrationRunner.java:81-95`; `tcpcalib.script:70-75`.

**Qué falla**: cada haz observa el eje inclinado del útil a una altura distinta, pero ambas rectas se resuelven como si pertenecieran al mismo plano. Para haces separados `Delta z` y una inclinación `theta`, el desplazamiento entre las secciones observadas es:

```text
eXY = Delta z * tan(theta)
```

Con `Delta z=3 mm`:

```text
theta=5 grados  -> 0,262 mm
theta=10 grados -> 0,529 mm
```

A 85 grados entre haces, el factor de amplificación es solo `1/sin(85 grados)=1,0038`, por lo que el problema es la descoplanaridad, no el condicionamiento. El stack nuevo aplica RX/RY pero no vuelve a medir XYZ, así que ese desplazamiento queda incorporado en la traslación. Esto cuantifica un alcance materialmente peor del ítem 15 ya documentado.

**Evidencia CAPTRON**: CAPTRON usa la misma proyección 2D en `scripts/adjust_angle.script:50-77`, pero itera y vuelve a sondear con el TCP actualizado en `:5-42`. Su sensor de un solo cuerpo tampoco introduce la separación entre dos horquillas independientes.

**Arreglo propuesto**: después de aplicar RX/RY, realizar al menos un nuevo círculo XYZ. Si se pospone, limitar mecánicamente `Delta z`: para mantener menos de 0,1 mm a 10 grados, `Delta z <= 0,57 mm`.

---

### [SEV-1] La pose Z se captura 40 ms después del flanco

**Dónde**: `tcpcalib.script:160-168,184-195`; stack legacy `search_z.script:41-49`; escalado de velocidad en `TCPCalibrationContribution.java:404-406`.

**Qué falla**: al detectar ambos haces altos se ejecuta `sleep(0.04)` mientras el robot sigue moviéndose y solo después se captura `pz`. Con velocidad nominal de 30 mm/s, la inmersión va a 6 mm/s:

```text
sobrepaso = 6 * 0,04 = 0,24 mm
```

Con modos Slow/Normal/Fast, el sobrepaso es aproximadamente 0,12/0,24/0,36 mm. Si el referenciado se hizo a velocidad normal y el runtime usa Slow o Fast, entra un error Z diferencial de +/-0,12 mm. Además, `pRel` se captura antes de su espera pero `pz` después, por lo que la recolocación final queda desplazada aproximadamente la mitad del sobrepaso.

**Evidencia CAPTRON**: el mismo orden aparece en `CAPTRON scripts/search_z.script:41-49`; es paridad, pero también un sesgo metrológico real.

**Arreglo propuesto**: capturar `pz` inmediatamente al detectar el flanco, esperar 40 ms solo para confirmar que ambas entradas siguen altas y después detener el movimiento.

---

### [SEV-1] `meanDiameter` confunde componente tangencial de la cuerda con diámetro

**Dónde**: `TCPCalibrationMaths.java:120,144-152`; uso y tolerancia en `TCPCalibrationRunner.java:60,68-79`.

**Qué falla**: si el círculo está centrado respecto al haz, la distancia entre flancos sí coincide con el ancho efectivo `D`. La sagita vale:

```text
R6, D1,2  -> 0,030 mm
R14, D18  -> 3,276 mm
```

pero desplaza el punto medio a lo largo del haz y no daña la recta.

Con el haz separado `b` del centro del círculo, los flancos son:

```text
P+/- = (sqrt(R^2-(b+/-D/2)^2), b+/-D/2)
```

y el código mide:

```text
L = |P+ - P-| > D
```

Para `b=3 mm`:

```text
R6, D1,2  -> L=1,3888 mm, error +0,1888 mm
R14, D18  -> L=18,8035 mm, error +0,8035 mm
```

El offset `real-referenciado` solo cancela este error si `b` no cambia. Una deriva XY se convierte falsamente en deriva de diámetro.

**Evidencia CAPTRON**: CAPTRON hace la misma distancia directa en `scripts/gl.script:69-81`; no hay compensación por radio, ancho de haz o descentramiento.

**Arreglo propuesto**: estimar primero la dirección unitaria del haz y medir cada par por su componente perpendicular:

```text
D = |(P2-P1) x u_haz|
```

El ancho óptico constante seguirá cancelándose mediante el diámetro referenciado.

---

### [SEV-1] La identidad de un TCP puede cambiar mientras un nodo o una calibración lo usa

**Dónde**: `TCPCalibrationService.java:48-50`; `TCPCalibrationContribution.java:163-183`; `TcpStore.java:47-53,82-87,154-160`; `InstallationContribution.java:103-120,493-499`; `OverviewCard.java:96-104,314-370`.

**Qué falla**: `actTcpId` no se materializa al crear el nodo. Si solo existe el slot 2, el nodo muestra el 2 mediante `firstTcpId()`, pero no guarda nada. Si después se crea el slot 1, el mismo nodo cambia silenciosamente al 1.

Además, durante `Calibrar (test)` solo se deshabilita el botón Calibrate; siguen disponibles borrar y crear. Si se borra el slot sondeado y se reutiliza su número, el worker guarda corrección, diámetro, `calibrated` y `refPose` en el TCP nuevo.

**Evidencia CAPTRON**: `tcp_action/A.java:54-64` usa `ctId=-1` si no se seleccionó; `tcp_action/C.java:134-137` deja el nodo indefinido. Durante calibración live deshabilita todos los controles en `tcp/inst/A/C.java:343-351`.

**Arreglo propuesto**: persistir `K_ACT_TCPID` para nodos `NEW`; dejar indefinidos los antiguos sin clave. Añadir una generación/UUID por slot o bloquear altas/bajas mientras haya una sesión y validar la generación antes de persistir.

---

### [SEV-1] Una respuesta 5510 truncada puede guardar una referencia falsa

**Dónde**: `CalibrationController.java:46-59`; `CalibrationResult.java:57-77`; persistencia en `InstallationContribution.java:448-454`. El mismo puerto se abre en `RepeatabilityController.java:42-55`.

**Qué falla**: una conexión que termine enviando solo `"0"` produce `received=true`, `status=0`, corrección cero y diámetro cero. `isSuccess()` lo acepta y el wizard marca el TCP como calibrado. Además, 5510 se enlaza a todas las interfaces y se acepta al primer cliente, aunque el robot conecta por `127.0.0.1`; otro equipo de la red podría inyectar ese resultado.

**Evidencia CAPTRON**: n/a; es el protocolo propio GIA.

**Arreglo propuesto**: enlazar 5510 exclusivamente a `127.0.0.1` y exigir exactamente ocho campos finitos, estado entero y frame completo antes de aceptar el resultado.

## Hallazgos SEV-2

### [SEV-2] `overrun=10 grados` no garantiza cuatro flancos y la boquilla Ø18/R14 no puede completar el círculo

**Dónde**: `tcpcalib.script:52-68,70-75,92-105`; `CircleData.java:16-22`; `Const.java:69-71,84-100`; `SetupWizard.java:269-281,314-325`.

**Qué falla**: el hilo comienza desarmado y espera ambos haces libres. Para un útil circular:

```text
alpha = asin((D/2)/R)
```

Si el círculo empieza centrado dentro del haz hace falta `overrun>alpha`; para cualquier fase dentro del sector bloqueado puede hacer falta `overrun>2*alpha`.

```text
R6, Ø1,2  -> alpha=5,739 grados, garantía de fase >11,478 grados
R14, Ø18  -> alpha=40,005 grados, garantía de fase >80,010 grados
```

El default de 10 grados no garantiza el hilo Ø1,2 para cualquier orientación de las horquillas. Con Ø18/R14, el último haz queda normalmente con tres flancos; incluso el máximo configurable de 45 grados es insuficiente para cualquier fase.

Además, con el margen declarado de 1,5 mm:

```text
Rmín a 90 grados = 14,849 mm
Rmín a 85 grados = 15,542 mm
```

Por tanto, R14/Ø18 no cumple la propia regla física. El wizard solo avisa y permite ejecutar.

**Evidencia CAPTRON**: mismo armado en `scripts/interrupt_points.script:10-28,65-86`, pero CAPTRON usa 15 grados por defecto en `tcp/inst/B/C.java:403-415`. Ese valor sí cubre el caso ideal R6/Ø1,2.

**Arreglo propuesto**: validar conjuntamente radio, diámetro efectivo, ángulo mínimo de 85 grados y overrun. Rechazar combinaciones imposibles o ampliar la trayectoria hasta completar cuatro flancos y salir con ambos haces libres.

---

### [SEV-2] Search Z acepta 3 mm aunque con 3 mm de descoplanaridad necesita al menos 4-5 mm

**Dónde**: `Const.java:88`; `SetupWizard.java:278`; `tcpcalib.script:147-177`.

**Qué falla**: el teach exige la punta 1-2 mm por debajo del haz inferior y el script no termina la retracción hasta que ambos haces están libres. Con haces separados 3 mm:

```text
recorrido mínimo ideal = 3 + (1..2) = 4..5 mm
```

No incluye ancho del haz, filtro ni parada. La UI acepta 3 mm, que termina necesariamente en `SEARCH_Z_FAILED` en ese montaje. El default de 8 mm sí tiene margen.

**Evidencia CAPTRON**: mismo criterio de ambos bajos en `scripts/search_z.script:18-36`; sus variantes inicializan Search Z a 20 mm en `tcp/inst/B/C.java:403-415`.

**Arreglo propuesto**: elevar el mínimo práctico a 6 mm o validarlo contra la descoplanaridad máxima declarada más profundidad de teach y margen.

---

### [SEV-2] Stop no tiene propiedad de sesión y no desbloquea el stack legacy

**Dónde**: `OverviewCard.java:125-132,225-228`; `CalibrationController.java:44-73`; `RepeatabilityController.java:39-79`; `DiagnosticsCard.java:134-159,383-419`.

**Qué falla**: Stop está habilitado siempre que el TCP esté configurado, aunque no haya una calibración activa. Pulsarlo puede enviar `halt` por 30001 y un programa de parada por 30002 durante un programa ajeno.

Cuando sí se detiene el wizard legacy, `halt` no cierra el `ServerSocket` que espera en 5510. Permanece ligado hasta 120 s; un reintento inmediato falla al abrir el puerto. El test de repetibilidad tiene `stop()` en el controlador, pero ningún botón lo invoca: el Stop visible solo detiene el monitor diagnóstico.

**Evidencia CAPTRON**: el botón Stop solo se habilita mientras `J` indica calibración activa en `tcp/inst/A/C.java:311-324`; `tcp/inst/A.java:570-578` también cancela el worker/socket asociado.

**Arreglo propuesto**: introducir una sesión activa con token y referencia al socket. Habilitar Stop solo para esa sesión y cerrar su socket al cancelar. Añadir Stop específico a repetibilidad.

---

### [SEV-2] El robot recibe `OK` antes de que el referenciado quede persistido

**Dónde**: `CalibrationServer.java:132-136,207-220`; `InstallationContribution.java:99-120`.

**Qué falla**: `sendDone(result)` responde al robot antes de `maybePersist`. El sink solo encola la escritura con `invokeLater` y devuelve. Si cambia la instalación, falla el DataModel o se detiene el bundle antes de ejecutar el EDT, el programa ya continuó con estado OK aunque no exista la referencia.

**Evidencia CAPTRON**: n/a; la persistencia desde un nodo runtime es una adaptación GIA.

**Arreglo propuesto**: convertir el sink en una operación confirmable y enviar `OP_DONE/OK` solo después del commit. Añadir al final del enum un estado específico de fallo de persistencia.

## Hallazgos SEV-3

### [SEV-3] Otros defaults no persistidos cambian silenciosamente al actualizar

**Dónde**: `Const.java:130,139`; `TCPCalibrationContribution.java:203-205`; `ProgErrHandlingContribution.java:68-70`; `TCPCalibrationService.java:48-50`; `ProgErrHandlingService.java:42-44`.

**Qué falla**: los nodos nuevos no materializan sus defaults usando `CreationContext`. La versión inicial (`80536fc`) tenía Approach Z=50 mm y reintentos=1; ahora son 30 mm y 2. Un nodo antiguo que nunca editó esos campos carece de clave y cambia silenciosamente al cargar. Reducir Approach de 50 a 30 mm puede reducir una separación de seguridad ya validada.

**Evidencia CAPTRON**: n/a.

**Arreglo propuesto**: versionar ambos DataModel. Para nodos antiguos sin clave, materializar 50 mm/1; para nodos `NEW`, guardar 30 mm/2 al crearlos.

---

### [SEV-3] Los errores geométricos -1/-2/-3 se colapsan en un único estado

**Dónde**: `TCPCalibrationMaths.java:88-92,116-141`; `TCPCalibrationRunner.java:35-42`; `TCPCalibrationResult.java:11-20`.

**Qué falla**: número incorrecto de flancos, rectas paralelas e intersección fuera del radio terminan todos como `NO_INTERSECT`. El operador no puede distinguir entre ajustar overrun, revisar geometría o corregir el centro enseñado.

**Evidencia CAPTRON**: `scripts/inst.script:30-43` conserva estados distintos -1, -2 y -3.

**Arreglo propuesto**: añadir al final del enum estados para `WRONG_POINT_COUNT` e `INTERSECT_TOO_FAR`, conservando `NO_INTERSECT` para paralelas, y mapear los tres resultados sin cambiar ordinales existentes.

---

### [SEV-3] `CalibrationServer.stop()` no termina sesiones 5512 activas

**Dónde**: `CalibrationServer.java:95-101,103-139`; `Activator.java:41-44`.

**Qué falla**: al parar o actualizar el bundle solo se cierra el socket de escucha. Las sesiones aceptadas no están registradas y pueden seguir hasta 120 s, conservar el classloader antiguo y ejecutar un sink después de detener la URCap.

**Evidencia CAPTRON**: n/a.

**Arreglo propuesto**: registrar sockets/threads de sesión, cerrarlos en `stop()`, esperar su terminación y limpiar `resultSink`.

---

### [SEV-3] Diagnostics conserva indefinidamente una pose realtime obsoleta

**Dónde**: `RobotRealtimeReader.java:45-64,77-84`; `DiagnosticsCard.java:287-304,340-375`.

**Qué falla**: tras una lectura válida, cualquier fallo posterior de 30003 conserva `message` y `latestPose`. Los flancos nuevos se registran con una pose antigua como si fuese actual.

**Evidencia CAPTRON**: n/a.

**Arreglo propuesto**: invalidar la muestra al comenzar cada lectura, devolver timestamp/estado de éxito y mostrar `pose n/a` cuando expire.

## Áreas revisadas sin hallazgos

- `midpoint()` reconstruye correctamente la recta central: la sagita desplaza el punto a lo largo del haz, no fuera de él.
- `intersect2D()` no presupone 90 grados. A 85 grados el condicionamiento es prácticamente idéntico al de 90 grados.
- La fórmula `R * pSearch^-1 * pRef` y las transformadas Rodrigues son correctas cuando el TCP activo coincide con la base `R`.
- Una descoplanaridad fija de 3 mm no introduce por sí sola error XY con útil vertical; Search Z referencia siempre el último haz encontrado y ese offset cancela diferencialmente.
- Los signos de Approach/Search/Immerse e `invertZ` son coherentes.
- Las conversiones m/mm, el INIT de 25 campos, mensajes `C`/`Z`, tuple de nueve floats y ordinales actuales están alineados.
- `CalibCsv` rechaza poses `C`/`Z` incompletas y propaga correctamente `LOW/SEARCH/IMMERSE`.
- Set TCP en los programas live normales, retirada a approach, restauración tras Check/Validate, no restauración tras Recalibrate, gate, light check y bucle de reintento tienen paridad, salvo los fallos de TCP activo indicados arriba.

## Verificación ejecutada

Comando:

```bash
mvn -o clean package -Pursim
```

Resultado:

```text
BUILD SUCCESS
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
```

Los tests actuales no ejercitan:

- Referenciado con `j != h`.
- TCP calibrado no identidad en Check/Validate.
- RX/RY con corrección XYZ previa.
- Diámetro con círculo descentrado.
- Overrun con boquilla Ø18 y radio 14 mm.
- Descoplanaridad combinada con inclinación.
