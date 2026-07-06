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

## Prioridad media (robustez / ergonomía)

6. **Límites de teclado por parámetro (validación de rangos en el wizard).** CAPTRON valida cada
   campo con rangos por variante de sensor (visto en `tcp/inst/A/D.java`: search Z 10–999,
   overrun 5–45; radio/velocidad/aceleración por modelo en `tcp/inst/B/A.java`). Nuestro wizard
   acepta cualquier número; con radio < regla del seno de 45° o overrun bajo, el fallo aparece
   después y lejos de la causa. Añadir validación con la regla física documentada en `Const`
   (incluida la advertencia si `realDiameterMm` configurado exige radio ≥ ~13–16 mm por boquilla).

7. **Tolerancias min/max asimétricas.** CAPTRON define Min y Max por eje (X/Y/Z/Ø, defaults
   -999/999), lo que permite bandas asimétricas (p. ej. aceptar hilo más largo que corto).
   Nosotros solo tenemos ±banda. Modelo en `tcp_action/A.java` (arrays `L.S()`/`L.K()`).

8. **Columna "Previous" en la pestaña de tolerancias del nodo.** CAPTRON muestra junto a Min/Max
   la última desviación medida por ese nodo (XYZ, Ø y ángulo), lo que facilita ajustar bandas
   con datos reales. Nosotros solo mostramos la corrección en el Overview de instalación.

9. **Botones "Move Start" / "Move Approach" en el nodo (pestaña Assignment).** Utilidad de puesta
   en marcha de CAPTRON: llevar el robot a la posición de inicio (intersección con corrección
   aplicada para Check/Validate, centro enseñado para Recalibrate) o a la de aproximación.
   Nosotros solo tenemos el mover-al-centro del flujo guiado del test.

11. **Funciones script públicas (`gia_*`).** CAPTRON expone `cap_isActionOk`, `cap_getStatus`,
    `cap_getStatusMsg`, `cap_getCorrectionMM`, `cap_getDiameterMM`, `cap_activateTCP`,
    `cap_setTCP` para usarlas en expresiones/If del programa. Nosotros dejamos globals crudas
    (`giaTcpOk`, `tcpc__rtStatus`, `tcpc__rtTcp`, `tcpc__rtDiam`) sin envoltorios con nombre ni
    mensaje de estado legible.

12. **Escrituras del DataModel fuera de UndoableChanges.** `TcpStore.setCalibrationResult` (y el
    sink desde el servidor 5512) escriben el modelo directamente vía `invokeLater`. Funciona hoy,
    pero algunas versiones de PolyScope lo penalizan; endurecer con
    `UndoRedoManager.recordChanges` sería lo canónico.

13. **Timeout agregado del runtime demasiado largo.** `tcpc__rtCalib` tolera 15 misses × 20 s
    (hasta 5 min) y los sockets live esperan 120 s: un fallo de red deja al operario mirando la
    pantalla demasiado tiempo. Ajustar a valores más cortos con reintento explícito.

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

20. **"Iniciar referenciado" en modo pasivo para modo Local.** En un e-Series en modo Local la
    inyección por 30001/30002 está bloqueada (el robot del usuario lleva pendant 3PE y no puede
    usar Remote Control). Propuesta acordada como candidata: al pulsar "Iniciar referenciado" en
    modo Local, en vez del aviso, el wizard pasa a modo espera ("abre el programa GIA_Referenciar
    y pulsa Play...") y se completa solo cuando el sink del `CalibrationServer` (5512) persiste el
    referenciado del programa. Cero cambios de protocolo; solo UI del wizard + callback del sink.

## Validación pendiente (no es código)

18. **Validación en robot real** (`mvn install -Premote`, checklist del README): polaridad en
    Diagnostics, N calibraciones seguidas para sigma/rango de repetibilidad, exactitud contra
    referencia independiente, y comprobar en hardware los cambios recientes (recolocación final
    entre flancos, guard del search Z, Stop del test, retirada del nodo a approach).
19. **Re-referenciar cada TCP una vez** tras instalar esta versión (para que adopten la
    referencia de pose medida) siguiendo la guía de teach oficial: ambos haces cortados y solo
    1–2 mm de inmersión.
