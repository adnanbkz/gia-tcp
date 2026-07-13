# Calibrador de TCP GIA — Especificación de montaje y cableado (2 sensores)

**Estación:** Estación_Cobot_HEM (Pronimetal) — cobot UR sobre eje lineal FESTO 6 m, antorcha KEMPPI.
**Sensor:** 2 × SensoPart **FGL 50-IK-50-PS-M4** (ref. 832-11022), barrera de horquilla IR, salida PNP.
**Objetivo:** calibrar el TCP (punta de hilo/antorcha) en XYZ (+ ángulo opcional) por el método de
**dos barreras cruzadas** + sondeo circular + intersección de dos líneas.

Esta spec deriva directamente del algoritmo de la URCap GIA TCP Calibrator. Cumplirla es lo que hace
que la calibración funcione; cualquier desviación hay que validarla con la pantalla **Diagnostics** de la URCap.

---

## 1. Geometría de los dos sensores (lo crítico)

Los dos FGL 50 se montan formando una **cruz**: sus dos haces **coplanarios**, **perpendiculares (~90°)**
y **cruzándose** en el centro de la zona de calibración. El útil entra **vertical desde arriba** por el cruce.

```
   vista en planta (mirando desde arriba)

            arm B (emisor)
                 │
                 │  haz B
   ──────────────┼──────────────   haz A
   arm A         │          arm A
   (emisor)      │          (receptor)
                 │
            arm B (receptor)

   · El útil baja vertical (Z- del robot) por el centro de la cruz.
   · La URCap hace un círculo de Ø12 mm (radio 6 mm por defecto, dimensionado
     para sondear la punta del hilo) alrededor del centro enseñado; ese
     círculo debe cruzar AMBOS haces.
```

Requisitos:

1. **Coplanarios en Z.** Los dos haces a la misma altura (idealmente; tolerancia **≤ 2–3 mm** de
   diferencia en Z entre haz A y haz B). El sondeo de XY es un círculo en un plano horizontal: si un haz
   queda muy por encima/debajo del otro, el círculo no lo cruza.
2. **Perpendiculares (~90°)** entre sí. No tiene que ser exacto, pero cuanto más cerca de 90°, mejor
   condicionada queda la intersección de las dos líneas.
3. **Cruce centrado** en la zona de trabajo. El punto donde se cruzan los dos haces es la referencia;
   ahí se enseña el "centro" en la URCap. **Profundidad del teach (guía oficial CAPTRON):** el centro
   se enseña con AMBOS haces cortados y con la punta inmersa solo **1–2 mm** por debajo del plano de
   haces — no más profundo. Con más inmersión, la retracción del Search Z tarda más en liberar y el
   sistema pierde margen; con menos, el círculo puede no cortar los haces.
4. **Apertura libre para el círculo de sondeo.** El útil describe un círculo de **Ø12 mm** (radio 6 mm
   por defecto, para sondear la **punta del hilo**). El envolvente real = Ø círculo + Ø boquilla. Con
   boquilla de ~16–20 mm el envolvente ronda **28–32 mm**, holgado en la ventana de la horquilla FGL 50.
   **Regla del radio mínimo:** para que la captura de flancos se arme, ambos haces deben quedar libres
   a la vez, y sobre el círculo eso solo ocurre cerca de las bisectrices → radio > (radio del útil +
   medio ancho de haz + margen) / sen 45°. Si lo que corta los haces es la **boquilla** (Ø16–20) en vez
   del hilo, el radio debe subirse a **≥ 13–16 mm** en la URCap (y el envolvente crece en consecuencia).
5. **Hueco vertical en ambos lados del plano de los haces.** La lógica actual trata los campos como
   distancias positivas y firma el movimiento según `invertZ`, siguiendo CAPTRON: la aproximación y la búsqueda Z van por
   el lado seguro de retracción, y la inmersión vuelve en sentido contrario. Por defecto: Approach Z
   **30 mm**, Search Z **8 mm**, Immerse Z **5 mm**. Debe haber espacio libre para esos recorridos,
   sin estructura ni brazos de la horquilla en la trayectoria de la punta.
6. **Rigidez.** El conjunto montado **rígido y sin flexión** sobre estructura fija de la célula. La
   repetibilidad de la calibración depende directamente de esto. Mejor sobre punto fijo (el robot viaja
   por el eje hasta el calibrador) que sobre algo que vibre.

---

## 2. Polaridad de los sensores (obligatorio)

El algoritmo de la URCap interpreta:

> **entrada digital en HIGH (24 V) = haz CORTADO** (útil dentro del haz)
> **entrada digital en LOW (0 V) = haz LIBRE**

Por tanto **ambos FGL 50 deben configurarse en modo "oscuro" (dark-operate)**: la salida PNP se activa
(24 V) **cuando el haz está bloqueado**. En el FGL 50 esto es el ajuste **N.C./oscuro** (según
nomenclatura SensoPart; es el conmutable N.O./N.C. del sensor). Hacer el teach-in del sensor con el
útil **fuera** del haz (salida = 0 V) y confirmar que al meter el útil la salida pasa a 24 V.

**Validación con la URCap:** en *Installation → GIA TCP Calibrator → Diagnostics*, meter el útil en cada
haz y comprobar que el indicador de esa entrada se pone **verde/HIGH**. Si se pone al revés, invertir el
N.O./N.C. del sensor (o invertir esa entrada en la configuración de E/S del PolyScope).

---

## 3. Cableado eléctrico

Cada FGL 50: conector **M8, 4 pines, PNP**. Pinout estándar (confirmar con la hoja de datos SensoPart):

| Pin M8 | Color típico | Función                |
|--------|--------------|------------------------|
| 1      | marrón       | +24 V (L+)             |
| 3      | azul         | 0 V (L−)               |
| 4      | negro        | Salida Q (PNP)         |
| 2      | blanco       | 2ª salida / no usada   |

- Cada salida Q (pin 4) → una **entrada digital del UR**. Recomendado usar **entradas configurables**
  (`config_in[x]`) o estándar (`digital_in[x]`); la URCap soporta ambas.
- Alimentar los sensores con los **24 V del UR** (o fuente común con 0 V referenciado al del UR).
- Sensor A → entrada que en la URCap será **IO X**; sensor B → **IO Y** (el etiquetado es libre; lo que
  importa es que cada sensor vaya a una entrada distinta y se asigne en el wizard).
- **Filtro de entrada digital al mínimo** en la configuración del UR, para reducir la latencia del flanco
  (afecta a la precisión de la captura del punto de corte).

---

## 4. Aproximación del robot

- El útil debe poder llegar **vertical (eje Z de herramienta hacia abajo)** al centro de la cruz, con
  trayectoria libre de colisión desde la posición de parking sobre el eje lineal.
- Espacio para el **círculo de sondeo Ø12 mm** alrededor del centro, en horizontal, sin tocar los brazos
  (más el diámetro de la boquilla; si se sondea la boquilla, ver la regla del radio mínimo del punto 1.4).
- Espacio para la **aproximación/retracción Z** (30 mm / 8 mm por defecto) y para la inmersión de 5 mm
  al otro lado del plano de haces.
- El calibrador en posición **fija y accesible** a lo largo de la carrera del eje (un extremo es buena
  opción), de forma que se pueda calibrar antes de cada serie / tras cambio de boquilla.

---

## 5. Resumen de comprobaciones antes de la primera calibración

- [ ] Dos FGL 50 montados en cruz, haces coplanarios (≤2–3 mm en Z) y ~perpendiculares.
- [ ] Cruce de haces accesible vertical por el útil; Ø12 mm libres en horizontal más margen de boquilla.
- [ ] Espacio vertical libre para Approach Z 30 mm, Search Z 8 mm e Immerse Z 5 mm.
- [ ] Boquilla real cabe en la horquilla con el círculo de sondeo (verificar diámetro).
- [ ] Ambos sensores en **dark-operate** (haz cortado → 24 V), comprobado en *Diagnostics* (verde = cortado).
- [ ] Cada salida a una entrada digital distinta del UR; filtro de entrada al mínimo.
- [ ] Montaje rígido sobre estructura fija.
- [ ] Trayectoria de aproximación vertical sin colisión validada en vacío.

---

## 6. Parámetros por defecto de la URCap (ajustables por TCP)

| Parámetro            | Valor por defecto | Nota |
|----------------------|-------------------|------|
| Radio de sondeo      | 6 mm              | Ø círculo 12 mm, para la punta del hilo; con boquilla subir a ≥13–16 mm (regla del punto 1.4) |
| Velocidad de sondeo  | 30 mm/s           | Bajar para más precisión de flanco en hardware real |
| Aceleración          | 100 mm/s²         | |
| Overrun              | 15°               | Margen angular del círculo (default CAPTRON); debe cubrir el arco bloqueado completo 2·asen((Ø útil/2)/radio) — con hilo Ø1,2 y radio 6 son ~11,5° |
| Search Z             | 8 mm              | Recorrido de retracción para liberar los haces |
| Approach Z           | 30 mm             | Aproximación desde el lado seguro antes del sondeo runtime |
| Immerse Z            | 5 mm              | Vuelta a través del plano de haces para capturar Z |
| Invert Z             | desactivado       | Default CAPTRON; activar solo si el montaje necesita invertir los sentidos Z |
| Ajuste de ángulo     | desactivado       | RX/RY; dejar para una segunda fase, tras validar XYZ |

> El muestreo de flancos en la pantalla *Diagnostics* es a pocos Hz (validación de cableado/polaridad),
> **no metrología**. La captura de flanco precisa para la calibración ocurre dentro del URScript a la
> frecuencia del bucle de control (500 Hz). La repetibilidad final hay que medirla en robot real
> (calibrar el mismo TCP N veces y mirar la dispersión).
