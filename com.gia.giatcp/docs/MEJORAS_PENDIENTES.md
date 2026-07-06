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

- ~~20. "Iniciar referenciado" en modo pasivo para modo Local~~ (aplicada 2026-07-07): en modo
  Local el botón ya no muestra un error — pasa a espera pasiva con instrucciones (nodo GIA TCP
  + persist + Play), y el paso se completa solo cuando el sink del `CalibrationServer` (5512)
  persiste el referenciado de ese TCP (`InstallationContribution.ReferencingListener`). El
  botón Parar cancela la espera. Cero cambios de protocolo.

## Validación pendiente (no es código)

18. **Validación en robot real** (`mvn install -Premote`, checklist del README): polaridad en
    Diagnostics, N calibraciones seguidas para sigma/rango de repetibilidad, exactitud contra
    referencia independiente, y comprobar en hardware los cambios recientes (recolocación final
    entre flancos, guard del search Z, Stop del test, retirada del nodo a approach).
19. **Re-referenciar cada TCP una vez** tras instalar esta versión (para que adopten la
    referencia de pose medida) siguiendo la guía de teach oficial: ambos haces cortados y solo
    1–2 mm de inmersión.
