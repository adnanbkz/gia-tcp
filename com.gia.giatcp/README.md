# GIA TCP Calibrator (URCap)

URCap de GIA ROBOTICS para comprobar, validar y recalibrar el TCP de una herramienta con una estacion fija de dos barreras de luz. El hardware soportado actualmente es un unico modelo fisico: dos SensoPart FGL 50 montados cruzados.

## Hardware

- 2 x SensoPart FGL 50-IK-50-PS-M4, ref. 832-11022.
- Cada sensor es una horquilla de un solo haz; los dos se montan cruzados a unos 90 grados y coplanarios.
- Cada salida PNP va a una entrada digital distinta del UR.
- Ambos sensores deben ir en dark-operate: entrada HIGH = haz cortado.
- Ver `docs/montaje_calibrador_2sensores.md` para la especificacion mecanica y electrica.

## Flujo Actual

### Instalacion

- `Installation -> GIA TCP Calibrator` gestiona hasta 30 TCPs.
- El Setup Wizard configura variante, entradas X/Y, TCP de referencia, pose de centro y parametros de sondeo.
- `Fijar centro` solo acepta la pose si se enseno con el TCP de referencia activo (paridad CAPTRON); con otro TCP activo avisa y no guarda. Profundidad correcta del teach (guia oficial CAPTRON): ambos haces cortados y la punta inmersa solo 1-2 mm bajo el plano de haces.
- `Calibrar (test)` usa el flujo nuevo con secuencia guiada (como CAPTRON): comprueba Remote Control, activa el TCP de referencia (`set_tcp`), abre la pantalla de mover-robot hasta el centro ensenado y, al llegar, `SecondaryProbeTransport` envia `tcpcalib.script` por el puerto 30002 (cada programa inyectado empieza con `set_tcp(refTcp)`), recibe las poses crudas en `127.0.0.1:5511` y Java calcula la geometria con `TCPCalibrationMaths`.
- `Iniciar referenciado` del wizard y el test de repetibilidad siguen usando el camino legacy por primary 30001 y retorno 5510 (ambos comprueban Remote Control antes de inyectar). Siguen activos, pero el nodo runtime nuevo no depende de esa matematica URScript.

### Nodo De Programa

- El nodo activo se llama `GIA TCP` y lo registra `TCPCalibrationService`.
- `generateScript()` llama a `tcpc__rtCalib(...)` (Validate/Recalibrate) o a `tcpc__lightCheck(...)` (Check).
- El robot solo ejecuta movimiento realtime, lectura de entradas y captura de `get_actual_tcp_pose()`.
- `CalibrationServer` escucha en `127.0.0.1:5512`, dirige los pasos del robot y hace toda la geometria en Java.
- Acciones (paridad CAPTRON): `Check` es un chequeo ligero SIN sondeo (va a la pose referenciada,
  verifica que ambos haces quedan cortados y si no inmersa hasta Immerse Z para perdonar desgaste
  minimo) - es la accion rapida para usar entre soldaduras. `Validate` hace ese chequeo ligero y
  despues el sondeo completo con tolerancias. `Recalibrate` sondea directo desde el centro
  ensenado (tras cambio de boquilla la herramienta puede estar lejos).
- Bucle de reintento (paridad CAPTRON): la accion se repite hasta terminar OK; el nodo If Error
  corre en cada iteracion y decide entre reintento silencioso ("reintentar N veces", contador
  `giaTcpErrCount`) o ejecutar los hijos de recuperacion. Sin error handling: un solo intento.
  La recuperacion debe corregir la causa o parar el programa (Halt/aviso bloqueante).
- Gate de referenciado: el nodo queda amarillo si el TCP no esta referenciado; la excepcion es la
  pasada de referenciado (persist + Validate/Recalibrate), que es el camino oficial en modo Local.
- Estados de error: ademas de los genericos, `INPUT_LOW_AT_CENTER` (no corta ambos haces en la
  referencia: TCP/teach/herramienta doblada) e `IMMERSE_FAILED` (herramienta desgastada o ausente).
- Aproximacion en dos tramos: tramo rapido a `80/60 x factor` hasta el punto de aproximacion y
  tramo final a velocidad de sondeo (anclada en la pose referenciada para Check/Validate y en el
  centro ensenado para Recalibrate).
- Tolerancias por eje X/Y/Z y de diametro (fila con el simbolo de diametro): bandas [Min, Max]
  asimetricas (paridad CAPTRON); Validate/Recalibrate fallan si la correccion o el diametro
  sondeado salen de banda. La columna "Previous" muestra la ultima desviacion medida y el
  ultimo diametro sondeado de ese TCP, para ajustar bandas con datos reales.
- Pestaña Assignment: botones "Mover a inicio" / "Mover a aproximacion" (pantalla guiada de
  PolyScope) como utilidades de puesta en marcha.
- Funciones script publicas para expresiones/If del programa: gia_isActionOk(), gia_getStatus(),
  gia_getStatusMsg(), gia_getTCP(), gia_getDiameterMM(), gia_activateTCP().
- Los campos del wizard validan rangos al teclear (clamp estilo CAPTRON) y avisan si el radio
  de sondeo queda por debajo del minimo fisico para el diametro real configurado.
- Diametro (semantica CAPTRON): el referenciado guarda la medida cruda; en runtime se corrige con `diametro real - diametro referenciado` (si hay diametro real configurado) y se compara contra la banda.
- Referencia de pose (semantica CAPTRON): el referenciado guarda la pose medida en el plano de haces; las calibraciones posteriores miden su correccion contra esa pose (no contra el centro enseñado a mano), asi el error del teach desaparece tras el primer referenciado. El circulo de sondeo sigue arrancando en el centro enseñado. Re-enseñar el centro invalida la referencia y obliga a re-referenciar.
- Recalibrate puede escribir el TCP corregido en `giaActionTCP` o en una variable seleccionada, y opcionalmente aplicar `set_tcp`.
- Check y Validate respaldan el TCP activo (`get_tcp_offset`) y lo restauran al terminar, como CAPTRON; solo Recalibrate deja aplicado el TCP corregido.
- El nodo legacy `program/action/ProgTcpAction*` queda en el codigo fuente, pero no se registra en `Activator`.

## Distancias Y Convencion Z

Los campos de UI son magnitudes positivas. El signo real lo calcula `CalibParams` segun `invertZ`.

- Default CAPTRON: `invertZ=false`. Con herramienta top-down cuyo `+Z` apunta hacia abajo,
  Approach/Search usan `-toolZ` para retirarse hacia arriba e Immerse usa `+toolZ` para volver a entrar.
- Aproximacion segura: misma direccion que la retraccion de busqueda.
- Busqueda Z: se retrae hasta que ambos haces quedan libres.
- Inmersion Z: vuelve en direccion opuesta hasta cortar ambos haces.
- Defaults actuales (dimensionados para sondear la PUNTA DEL HILO): radio 6 mm, velocidad 30 mm/s, aceleracion 100 mm/s2, overrun 15 grados (default CAPTRON), Search Z 8 mm, Approach Z 30 mm, Immerse Z 5 mm.
- Radio minimo (fisica del armado): para que el hilo de flancos se arme, ambos haces deben quedar LIBRES a la vez, y en el circulo eso solo ocurre cerca de las bisectrices -> radio > (radio de herramienta + medio haz + margen) / sen 45. Hilo Ø1.2 -> 6 mm sobra; sondear la BOQUILLA Ø16-20 exige radio >= ~13-16 mm (subirlo por TCP en el wizard). El overrun (default 15 grados, CAPTRON) debe cubrir el arco bloqueado completo para cualquier fase: 2·asen((Ø util/2)/radio) ~ 11.5 grados con hilo Ø1.2 y radio 6; el wizard avisa si no llega.

Con el montaje por defecto esto implica aproximarse 30 mm por encima del cruce, bajar al centro, sondear un circulo de diametro 12 mm, retraer 8 mm para liberar los haces y volver a entrar hasta 5 mm al otro lado del centro ensenado. Tras el circulo ya no hay salto de vuelta al centro: la busqueda Z va directa al centro calculado. Al terminar, la punta se recoloca en el punto medio entre el flanco de liberacion y el de corte, de modo que queda justo entre los dos haces (paridad con la recolocacion final de CAPTRON).

## Arquitectura

- `tcpcalibration/math/TCPCalibrationMaths.java`: matematica pura Java, sin dependencia de UR API.
- `src/main/resources/scripts/tcpcalib.script`: primitivas realtime `tcpc__*`, sin calculo geometrico pesado.
- `TCPCalibrationRunner`: secuencia comun de calibracion sobre un `ProbeTransport`.
- `SecondaryProbeTransport`: calibracion live desde Java por secondary 30002.
- `ServerProbeTransport` + `CalibrationServer`: calibracion dentro del programa en ejecucion.
- `src/main/resources/scripts/inst.script`, `gl.script`, `interrupt_points.script`, `search_z.script`, `adjust_angle.script`: stack legacy usado aun por referenciado del wizard/repetibilidad y por el nodo viejo desactivado.

## Build

Ejecutar desde `com.gia.giatcp/`:

```bash
mvn test
mvn clean package
mvn install -Pursim
mvn install -Premote
```

El build genera:

- `target/GIATcp-1.0.jar`
- `target/GIA-TCP-1.0.urcap`

## Checklist En Hardware

1. Montar los dos FGL 50 en cruz, coplanarios y rigidos.
2. Verificar hueco para circulo de sondeo de diametro 12 mm mas diametro real de boquilla/hilo.
3. Verificar espacio vertical para aproximacion, retraccion Search Z e inmersion.
4. Configurar dark-operate y filtro de entrada digital al minimo.
5. En Diagnostics, confirmar que cada entrada pasa a HIGH/verde al cortar su haz.
6. Configurar TCP, ensenar centro y ejecutar `Calibrar (test)` o un nodo `GIA TCP` con `Guardar como referencia de instalacion`.
7. Medir repetibilidad con varios ciclos antes de fiarse de la correccion en produccion.
