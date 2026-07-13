# Mejoras pendientes (detectadas y NO aplicadas aún)

> Lista viva de todo lo identificado como mejorable durante el análisis de paridad con CAPTRON
> (decompilado 1.3.0 + manual oficial) que **todavía no se ha implementado**. Última
> actualización: 2026-07-06. Referencias: `docs/CONTEXTO.md` (lo ya aplicado),
> [Manual CAPTRON TCP 1.3.0](https://www.captron.com/fileadmin/user_upload/data/landingpage/Software_UR/Reference_Manual_URCap_CAPTRON_TCP_en_1.3.0_1.pdf).

## Aplicadas (2026-07-06) — detalles en CONTEXTO.md

- ~~1. Check ligero~~: Check ya no sondea (`tcpc__lightCheck`: verificación + inmersión de
  perdón); Validate hace el light check antes del sondeo completo, como CAPTRON.
- ~~2. Bucle de reintento del nodo~~: `while (not giaTcpOk)` + modos del If-Error
  (inmediato / reintentar N veces, contador `giaTcpErrCount`, default 2).
- ~~3. Estados diferenciados~~: `INPUT_LOW_AT_CENTER` e `IMMERSE_FAILED` nuevos;
  `Z;FAIL;LOW|SEARCH|IMMERSE` en el script; textos ES/EN.
- ~~4. Gate de referenciado~~: nodo amarillo con `TC_ISSUE_NOT_REFERENCED` si el TCP no está
  referenciado, salvo pasada de referenciado (persist + Validate/Recalibrate, el camino de
  modo Local).
- ~~5. Stop en el test live del nodo~~: botón Stop junto a "Calibrar (test)".
- ~~10. Aproximación rápida en dos tramos~~ (bonus): primer tramo a 80/60·factor, final a
  velocidad de sondeo; anclaje del approach en h() (Check/Validate) o j() (Recalibrate).

## Aplicadas (2026-07-07) — prioridad media completa

- ~~6. Validación de rangos en el wizard~~: clamp por parámetro (estilo CAPTRON) + aviso
  físico del radio mínimo (`Const.minRadiusForTool`).
- ~~7. Tolerancias min/max asimétricas~~: bandas [Mín, Máx] por eje y Ø en el nodo; INIT
  ampliado con 8 campos de cola; runner con `withinTolAsym` y banda direccional de Ø.
- ~~8. Columna "Previous"~~: última desviación X/Y/Z y último Ø sondeado por TCP
  (registro en `CalibrationServer`, alimentado por nodo runtime y test live).
- ~~9. Move Start / Move Approach~~: botones en la pestaña Assignment con pantalla guiada
  (h()/j() según acción + punto de aproximación); avisa si no puede activar el TCP de
  referencia (modo Local).
- ~~11. Funciones script `gia_*`~~: `gia_isActionOk`, `gia_getStatus`, `gia_getStatusMsg`,
  `gia_getTCP`, `gia_getDiameterMM`, `gia_activateTCP`.
- ~~12. UndoableChanges~~: verificado — todas las escrituras de nodos de PROGRAMA ya van en
  `recordChanges`; las de instalación no lo requieren (PolyScope no tiene undo en
  instalación) y van por el hilo UI. Cerrado sin cambios.
- ~~13. Timeouts~~: live 60/15 s, rtCalib 6×10 s, servidor 120 s por lectura.

## Prioridad baja / decisiones de arquitectura

14. **Migrar wizard (`Iniciar referenciado`) y repetibilidad al stack nuevo.** Siguen en el stack
    legacy (primary 30001, matemática en URScript, retorno 5510). Duplicidad de matemática y de
    scripts; decisión pendiente documentada en CONTEXTO. Tras migrar: limpiar `inst.script`,
    `gl.script`, `interrupt_points.script`, `search_z.script`, `adjust_angle.script` y el nodo
    legacy desactivado (`program/action/ProgTcpAction*`).

15. **RX/RY iterativo con re-centrado.** Nuestro ajuste de ángulo del stack nuevo es single-shot;
    CAPTRON itera (`iterator`/`accuracy`, termina antes si la corrección queda bajo el umbral) y
    re-centra entre iteraciones. Los parámetros ya existen en el wizard pero solo los usa el
    stack legacy. Fase 2: tras validar XYZ en hardware.

16. **Variantes de sensor con límites propios.** Solo existe la variante SensoPart FGL 50; la
    estructura de CAPTRON (radio/velocidad/aceleración máximos por modelo) sería el molde si se
    añade otra horquilla u otro tamaño (p. ej. sondear boquilla en vez de hilo como variante).

17. **Informe completo del mapa de comportamiento CAPTRON.** Quedó a medias la extracción
    sistemática (límites exactos por variante en `tcp/inst/B/A.java`, semántica fina min/max en
    `tcp_action/A.java`, textos i18n del grupo de tolerancias). Útil como referencia si se
    abordan los puntos 6 y 7.

## Revisión exhaustiva 2026-07-09 (hallazgos pendientes)

Corregidos en el momento: re-referenciado persist+Validate bloqueado por el light check contra
la referencia vieja (bucle infinito en modo Local); Stop del wizard bloqueando el EDT; el
listener pasivo recargaba la selección en vez del TCP esperado.

21. **Bucle de reintento sin salida con la config por defecto.** errH=true + If-Error vacío +
    fallo persistente → el robot cicla approach+sondeo para siempre (paridad CAPTRON, pero
    footgun). Opción: tope duro de intentos o insertar un Halt en la carpeta placeholder.
22. **Nodo GIA TCP anidado en el If-Error de otro corrompe el estado del exterior**
    (giaTcpOk/giaTcpErrCount/tcpc__rtStatus compartidos). CAPTRON usa arrays por id
    (`cap__actionStatus[id]`); replicarlo si se quiere soportar anidamiento.
23. **Ø con Mín=0/Máx=0 cae en silencio a la banda simétrica legacy** (±2 mm de K_TOL 'D').
    Unificar al mecanismo null=sin-override de XYZ y retirar el sentinel "ambos 0".
24. **Upgrade path invertZ**: la semántica del flag se invirtió respecto a versiones antiguas;
    instalaciones persistidas con invertZ=true se mueven al revés. Revisar el flag tras
    actualizar (o migrar el valor al cargar).
25. **Upgrade path diámetro**: referencias antiguas guardaban el Ø con el offset aplicado; el
    código nuevo asume medida cruda → banda de Ø siempre OUT hasta re-referenciar. Falta un
    aviso o migración.
26. **Puerto 5511 sin exclusión mutua**: dos tests live concurrentes (Overview + nodo) → el
    segundo da "sin respuesta" con el robot moviéndose. Añadir lock o mensaje "test en curso".
27. **Limpiezas señaladas**: statusText duplicado en dos vistas; flujo remote-check→activate→
    move duplicado (OverviewCard/TCPCalibrationView); INIT posicional de 25 campos → clave=valor;
    extraer isReferencingRun(); requestMove duplicado con InstallationContribution; mover
    LAST_RESULT a un registro propio (CalibrationResultHistory).

- ~~20. "Iniciar referenciado" en modo pasivo para modo Local~~ (aplicada 2026-07-07): en modo
  Local el botón ya no muestra un error — pasa a espera pasiva con instrucciones (nodo GIA TCP
  + persist + Play), y el paso se completa solo cuando el sink del `CalibrationServer` (5512)
  persiste el referenciado de ese TCP (`InstallationContribution.ReferencingListener`). El
  botón Parar cancela la espera. Cero cambios de protocolo.

## Auditoría externa 2026-07-13 (hallazgos verificados, pendientes)

Informe completo en `docs/AUDITORIA_TCP_FABLE5.md`; verificación propia contra código y
decompilado en CONTEXTO (entrada 2026-07-13). Corregidos en el momento: referenciado con
corrección identidad, RX/RY anclado en pSearchZ, tcpId materializado, 5510 endurecido.

28. **Check/Validate con el TCP calibrado activo** (CAPTRON `tcp_action/C.java:351-359` usa
    c.A() para Check/Validate; nosotros `set_tcp(refTcp)` siempre). Con la herramienta sin
    derivar es consistente (h se midió con refTcp); tras una deriva corregida por Recalibrate,
    Check da INPUT_LOW y Validate re-detecta la misma deriva para siempre (encadena con el
    item 21). ANTES de tocarlo: estudiar en el decompilado cómo persiste CAPTRON las
    recalibraciones de runtime (nuestro Recalibrate solo escribe la variable del programa).
    Incluye: mover el `set_tcp` dentro del `while` del nodo (un If-Error que cambie el TCP
    contamina el siguiente intento).
29. **Captura de `pz` 40 ms tarde en la inmersión** (`tcpc__searchZMsg`: sleep antes de
    capturar; la retracción captura antes del sleep). Paridad CAPTRON (`search_z.script`),
    sesgo ~0,24 mm que se cancela a velocidad igual; entra ±0,12 mm diferencial al mezclar
    velocidades (el test live no aplica el factor del nodo). Mejora: capturar al flanco y
    usar el sleep solo como confirmación.
30. **`meanDiameter` mide la cuerda completa** (componente tangencial incluida cuando el haz
    no pasa por el centro del círculo). Paridad CAPTRON (`gl.script:69-81`); impacto acotado
    por la banda XYZ (~0,02 mm con hilo). Mejora: proyectar cada par de flancos perpendicular
    a la dirección del haz.
31. **Validación conjunta radio/Ø/overrun/Search Z en el wizard.** El overrun default de 10°
    no cubre la peor fase ni con hilo (arco bloqueado 2·asin((Ø/2)/R) ≈ 11,5° con R6/Ø1,2;
    CAPTRON usa 15°); con boquilla Ø18/R14 se viola la regla del radio mínimo (14 < 14,85) y
    el wizard solo avisa. Search Z mínimo (3 mm) insuficiente con descoplanaridad de 3 mm +
    teach de 1-2 mm. Validar el conjunto y rechazar combinaciones imposibles.
32. **Stop sin propiedad de sesión.** El Stop del Overview actúa sin calibración activa (halt
    por 30001 + programa por 30002 durante un programa ajeno); tras parar el wizard legacy,
    el ServerSocket 5510 queda bloqueado hasta 120 s (reintento inmediato falla); el test de
    repetibilidad tiene `stop()` sin botón que lo invoque.
33. **El 5512 responde OP_DONE antes de persistir** (`sendDone` → `maybePersist` → sink con
    `invokeLater`): el programa continúa con OK aunque la escritura del DataModel falle.
    Convertir el sink en operación confirmable.
34. **Defaults de nodo no materializados** (mismo patrón que invertZ/diámetro, items 24-25):
    Approach Z pasó de 50→30 mm y reintentos de 1→2; nodos antiguos sin clave cambian en
    silencio al actualizar. Versionar el DataModel o materializar al crear (tcpId ya se
    materializa en openView desde 2026-07-13).
35. **Estados -1/-2/-3 colapsados en NO_INTERSECT** (número de flancos, paralelas,
    intersección lejana). El legacy los distingue (REF_ERR_1/2/3). Añadir estados al final
    del enum y mapear.
36. **`CalibrationServer.stop()` no cierra sesiones activas** (solo el socket de escucha):
    una sesión puede sobrevivir al bundle hasta 120 s y ejecutar un sink obsoleto.
37. **Diagnostics conserva una pose realtime obsoleta** tras un fallo de lectura del 30003:
    los flancos nuevos se registran con pose vieja. Invalidar la muestra por timestamp.

## Validación pendiente (no es código)

18. **Validación en robot real** (`mvn install -Premote`, checklist del README): polaridad en
    Diagnostics, N calibraciones seguidas para sigma/rango de repetibilidad, exactitud contra
    referencia independiente, y comprobar en hardware los cambios recientes (recolocación final
    entre flancos, guard del search Z, Stop del test, retirada del nodo a approach).
19. **Re-referenciar cada TCP una vez** tras instalar esta versión (para que adopten la
    referencia de pose medida) siguiendo la guía de teach oficial: ambos haces cortados y solo
    1–2 mm de inmersión.
