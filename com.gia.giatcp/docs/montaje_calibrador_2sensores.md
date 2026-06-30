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
   · La URCap hace un círculo de Ø30 mm (radio 15 mm por defecto)
     alrededor del centro enseñado; ese círculo debe cruzar AMBOS haces.
```

Requisitos:

1. **Coplanarios en Z.** Los dos haces a la misma altura (idealmente; tolerancia **≤ 2–3 mm** de
   diferencia en Z entre haz A y haz B). El sondeo de XY es un círculo en un plano horizontal: si un haz
   queda muy por encima/debajo del otro, el círculo no lo cruza.
2. **Perpendiculares (~90°)** entre sí. No tiene que ser exacto, pero cuanto más cerca de 90°, mejor
   condicionada queda la intersección de las dos líneas.
3. **Cruce centrado** en la zona de trabajo. El punto donde se cruzan los dos haces es la referencia;
   ahí se enseña el "centro" en la URCap.
4. **Apertura libre para el círculo de sondeo.** El útil describe un círculo de **Ø30 mm** (radio 15 mm
   por defecto). El envolvente real = Ø círculo + Ø boquilla. Con boquilla de ~16–20 mm el envolvente
   ronda **46–50 mm**. La horquilla FGL **50** da ~50 mm → **justo**. **A verificar:** diámetro real de
   la boquilla/punta; si queda apretado, o se bajan los brazos para no chocar, o se reduce el radio de
   sondeo a 10 mm en la URCap.
5. **Hueco vertical bajo el plano de los haces.** La búsqueda de Z baja el útil a través del plano
   (searchZ ≈ 20 mm por defecto). Tiene que haber **≥ 25–30 mm libres por debajo** del plano de los
   haces, sin estructura ni brazos de la horquilla, para que la punta pueda bajar sin colisión.
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
- Espacio para el **círculo de sondeo Ø30 mm** alrededor del centro, en horizontal, sin tocar los brazos.
- Espacio para la **inmersión en Z** (~20–30 mm hacia abajo) sin tocar la base del calibrador.
- El calibrador en posición **fija y accesible** a lo largo de la carrera del eje (un extremo es buena
  opción), de forma que se pueda calibrar antes de cada serie / tras cambio de boquilla.

---

## 5. Resumen de comprobaciones antes de la primera calibración

- [ ] Dos FGL 50 montados en cruz, haces coplanarios (≤2–3 mm en Z) y ~perpendiculares.
- [ ] Cruce de haces accesible vertical por el útil; Ø30 mm libres en horizontal; ≥25 mm libres abajo.
- [ ] Boquilla real cabe en la horquilla con el círculo de sondeo (verificar diámetro).
- [ ] Ambos sensores en **dark-operate** (haz cortado → 24 V), comprobado en *Diagnostics* (verde = cortado).
- [ ] Cada salida a una entrada digital distinta del UR; filtro de entrada al mínimo.
- [ ] Montaje rígido sobre estructura fija.
- [ ] Trayectoria de aproximación vertical sin colisión validada en vacío.

---

## 6. Parámetros por defecto de la URCap (ajustables por TCP)

| Parámetro            | Valor por defecto | Nota |
|----------------------|-------------------|------|
| Radio de sondeo      | 15 mm             | Ø círculo 30 mm; bajar a 10 mm si la horquilla queda justa |
| Velocidad de sondeo  | 50 mm/s           | Bajar para más precisión de flanco en hardware real |
| Aceleración          | 100 mm/s²         | |
| Overrun              | 10°               | Margen angular del círculo |
| Search Z             | 20 mm             | Recorrido de inmersión para hallar Z |
| Ajuste de ángulo     | desactivado       | RX/RY; dejar para una segunda fase, tras validar XYZ |

> El muestreo de flancos en la pantalla *Diagnostics* es a pocos Hz (validación de cableado/polaridad),
> **no metrología**. La captura de flanco precisa para la calibración ocurre dentro del URScript a la
> frecuencia del bucle de control (500 Hz). La repetibilidad final hay que medirla en robot real
> (calibrar el mismo TCP N veces y mirar la dispersión).
