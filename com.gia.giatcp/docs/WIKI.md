# GIA TCP Calibrator - Wiki Del Proyecto

URCap de GIA ROBOTICS para medir y corregir el TCP real de una antorcha/herramienta usando dos barreras de luz SensoPart cruzadas.

## 1. Idea General

El TCP es el punto que el robot considera la punta de la herramienta. Si la boquilla, el hilo o la antorcha cambian, el TCP real puede desplazarse y el programa suelda o trabaja fuera de sitio.

El calibrador GIA mide ese desplazamiento con dos haces opticos:

1. La herramienta entra verticalmente por el cruce de los dos haces.
2. El robot describe un circulo pequeno alrededor del centro ensenado.
3. Cada haz se corta dos veces; en cada flanco se captura `get_actual_tcp_pose()`.
4. Java calcula dos rectas, su interseccion XY y la correccion de TCP.
5. La busqueda Z retrae hasta liberar los haces y vuelve a entrar para capturar la altura del plano.
6. Opcionalmente se mide inclinacion RX/RY con un segundo circulo desplazado en Z.

## 2. Hardware Soportado

El unico modelo fisico disponible ahora es:

- 2 x SensoPart FGL 50-IK-50-PS-M4.
- Dos horquillas de un solo haz, cruzadas a unos 90 grados.
- Haces coplanarios, cruzados en el centro de calibracion.
- Salida PNP a dos entradas digitales del UR.
- Dark-operate obligatorio: HIGH = haz cortado.

La especificacion mecanica esta en `docs/montaje_calibrador_2sensores.md`.

## 3. Flujo De Trabajo

### 3.1 Puesta En Marcha

1. Montar los sensores cruzados, coplanarios y con estructura rigida.
2. Cablear cada salida a una entrada digital distinta.
3. Poner los FGL 50 en dark-operate.
4. Reducir el filtro de entrada digital del UR al minimo.
5. En Diagnostics, comprobar que cada entrada se pone HIGH al cortar su haz.

### 3.2 Configurar Un TCP

En `Installation -> GIA TCP Calibrator`:

1. Crear o seleccionar TCP.
2. Elegir variante `SensoPart FGL 50-IK-50-PS-M4`.
3. Asignar entradas X/Y.
4. Seleccionar TCP de referencia del robot.
5. Mover la herramienta al centro de los haces y pulsar `Fijar centro`. El TCP activo en la
   pantalla de movimiento debe ser el TCP de referencia; con otro TCP la pose se rechaza
   (la geometria posterior asume que el centro se enseno con ese TCP). Profundidad correcta
   (guia CAPTRON): ambos haces cortados y la punta inmersa solo 1-2 mm bajo el plano de haces.
6. Revisar parametros: radio, velocidad, aceleracion, Search Z, `invertZ`, diametro nominal y ajuste de angulo.
7. Referenciar con `Calibrar (test)` o con el nodo `GIA TCP` marcando `Guardar como referencia de instalacion`.

`Calibrar (test)` sigue la secuencia guiada de CAPTRON: comprueba Remote Control, activa el
TCP de referencia con `set_tcp`, pide llevar el robot al centro ensenado con la pantalla de
mover-robot y sondea automaticamente al llegar. Cada programa inyectado empieza con
`set_tcp(refTcp)`, asi el resultado no depende del TCP que estuviera activo.

### 3.3 Usar En Programa

El nodo activo es `GIA TCP`.

- Check (ligero, paridad CAPTRON): NO sondea. Va a la pose referenciada, comprueba que ambos
  haces quedan cortados y, si no, inmersa despacio hasta Immerse Z para perdonar desgaste
  minimo; solo falla si aun asi no corta ambos haces. Es la accion rapida entre soldaduras.
  Requiere TCP referenciado.
- Validate: chequeo ligero primero (falla rapido con estado preciso si la herramienta no esta)
  y despues mide el circulo completo; falla si la correccion sale de tolerancia.
- Referencia de pose (semantica CAPTRON): la correccion se mide contra la pose capturada en el
  referenciado (no contra el centro enseñado); el centro enseñado solo es el punto de arranque
  del sondeo. Re-enseñar el centro borra la referencia y hay que volver a referenciar.
- Tolerancias: banda +/- por eje X/Y/Z y banda de diametro. El diametro sondeado se corrige
  con `real - referenciado` (semantica CAPTRON; el referenciado guarda la medida cruda) y se
  compara contra el diametro real configurado, o contra el referenciado si no hay real.
- Recalibrate: mide desde el centro ensenado (sin chequeo previo: tras un cambio de boquilla la
  herramienta puede estar lejos), devuelve el TCP corregido, lo asigna a variable y
  opcionalmente ejecuta `set_tcp`.
- Bucle de reintento: la accion se repite hasta terminar OK (cada intento arranca y termina en
  el punto de aproximacion). Sin error handling se ejecuta una sola vez.
- If Error: nodo hijo auto-insertado que corre en cada iteracion del bucle. Dos modos: ejecutar
  la recuperacion inmediatamente, o "reintentar N veces" en silencio y solo entonces ejecutar la
  recuperacion (default 2, como CAPTRON). IMPORTANTE: si la recuperacion no corrige la causa ni
  detiene el programa (Halt / aviso bloqueante), el nodo sigue reintentando.
- Gate de referenciado: el nodo queda amarillo ("no definido") si el TCP no esta referenciado.
  Excepcion: una pasada con "Guardar como referencia" + Validate/Recalibrate, que es justamente
  como se referencia en modo Local.
- Guardar como referencia de instalacion: permite referenciar desde un programa con Play, util
  en modo Local. No aplica a Check (no mide).
- Stop: el test live del nodo ("Calibrar (test)") tiene boton Stop, igual que el del Overview.

## 4. Distancias Y Z

Los campos de distancia de UI son magnitudes positivas. El signo lo calcula `CalibParams`:

- `signedSearchZMm()`: distancia de busqueda que el script convierte en retraccion.
- `signedApproachZMm(...)`: aproximacion segura desde el lado de retraccion.
- `signedImmerseZMm(...)`: vuelta en direccion opuesta para recortar los haces.

Defaults actuales (dimensionados para sondear la punta del hilo):

| Parametro | Valor |
|---|---:|
| Radio de sondeo | 6 mm |
| Diametro del circulo | 12 mm |
| Velocidad | 30 mm/s |
| Aceleracion | 100 mm/s2 |
| Overrun | 10 grados |
| Search Z | 8 mm |
| Approach Z | 30 mm |
| Immerse Z | 5 mm |
| `invertZ` | false |

Radio minimo (fisica del armado del hilo de flancos): ambos haces deben quedar libres a la
vez para armar la captura, y sobre el circulo eso solo pasa cerca de las bisectrices, asi que
radio > (radio de herramienta + medio ancho de haz + margen) / sen 45. Con hilo Ø1.2 el
default de 6 mm sobra; para sondear una boquilla Ø16-20 hay que subir el radio a >= ~13-16 mm
en el wizard. El overrun de 10 grados no debe bajarse con radios pequenos. Los TCPs ya
creados conservan sus valores guardados: los defaults nuevos solo aplican a TCPs nuevos.

Con el default CAPTRON (`invertZ=false`), el montaje esperado top-down con `+Z` de herramienta hacia abajo aproxima y retrae en `-toolZ` (hacia arriba), y la inmersion vuelve en `+toolZ` (hacia abajo). Activar `invertZ` invierte esos tres sentidos.

Al final de la busqueda Z la punta no se queda clavada en el flanco de corte: se recoloca en el
punto medio entre el flanco de liberacion (retraccion) y el de corte (inmersion), quedando justo
entre los dos haces aunque las dos horquillas no sean perfectamente coplanarias. La pose medida
para la correccion Z sigue siendo el flanco de corte (semantica CAPTRON, sistema diferencial).

## 5. Arquitectura Actual

### 5.1 Stack Nuevo

Este es el camino principal para el nodo de programa y `Calibrar (test)`:

- `tcpcalibration/math/TCPCalibrationMaths.java`: pose algebra, interseccion 2D, centro, diametro, correccion TCP, tolerancias y RX/RY.
- `src/main/resources/scripts/tcpcalib.script`: solo movimiento, lectura de sensores y captura de poses.
- `TCPCalibrationRunner`: secuencia comun `probeCircle -> computeCenter -> searchZ -> correction -> angle opcional`.
- `SecondaryProbeTransport`: envia una primitiva por secondary 30002 y recibe una linea por 5511.
- `CalibrationServer` + `ServerProbeTransport`: servidor persistente 5512 para el nodo en runtime.

### 5.2 Stack Legacy Aun Presente

Quedan scripts `gia_tcp_*` y `gia__*`:

- Los usa `CalibrationController` para `Iniciar referenciado` del wizard.
- Los usa `RepeatabilityController` para el test de repetibilidad.
- Los usaria `program/action/ProgTcpAction*`, pero ese nodo esta desactivado en `Activator`.

Este stack sigue funcionando, pero no es el modelo objetivo para la geometria portable a Estun.

## 6. Comunicaciones

| Caso | Movimiento | Calculo | Puerto retorno |
|---|---|---|---|
| `Calibrar (test)` | Secondary 30002 | Java `TCPCalibrationMaths` | 5511 |
| Nodo `GIA TCP` en programa | `generateScript` del programa | Java `CalibrationServer` | 5512 persistente |
| Wizard `Iniciar referenciado` | Primary 30001 | Legacy URScript | 5510 |
| Repetibilidad | Primary 30001 | Legacy URScript | 5510 |
| Diagnostics | No mueve | IO API + realtime 30003 | No aplica |

## 7. Validacion En Hardware

El simulador no dispara las barreras, asi que la validacion real es obligatoria:

1. Comprobar polaridad con Diagnostics.
2. Ejecutar varias calibraciones con la misma posicion.
3. Revisar sigma y rango en repetibilidad.
4. Ajustar velocidad si hay dispersion.
5. Validar exactitud contra referencia independiente.
6. Activar ajuste RX/RY solo despues de validar XYZ.
