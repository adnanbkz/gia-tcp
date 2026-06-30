# Verificación matemática del algoritmo de calibración de TCP

He revisado una a una las operaciones matemáticas del algoritmo de calibración (los
cinco scripts de URScript que ejecutan el sondeo y el cálculo de la corrección). El
objetivo era confirmar que la formulación es correcta antes de pasar a la validación en
hardware. Resumo la comprobación punto por punto.

## 1. Generación de la trayectoria de sondeo
El algoritmo construye un círculo de radio configurable en el plano XY de la herramienta,
a partir de cuatro puntos cardinales (±X, ±Y) más un punto de sobrerrecorrido. Los
movimientos circulares (`movec`) encadenados trazan una circunferencia completa.
Geometría correcta.

## 2. Puntos de cruce con cada haz
En cada cruce de un haz se registran dos poses (flanco de entrada y de salida). El punto
de cruce real de la línea central de la herramienta se toma como el punto medio
posicional de ese par: P = P₁ + ½·(P₂ − P₁). Correcto.

## 3. Cambios de marco de referencia
Las poses se expresan en el marco de sondeo mediante la transformada homogénea estándar
(composición con la inversa del marco). Las conversiones base↔marco son correctas.

## 4. Intersección de las dos rectas
Cada haz queda definido por dos puntos de cruce, es decir, una recta. El centro real del
sensor es la intersección de ambas rectas, calculada con la formulación paramétrica
clásica:

    t = ((C − A) × d_b) / (d_a × d_b),  con el producto cruzado 2D cross(u,v) = uₓ·v_y − u_y·v_x
    I = A + t·d_a

La implementación coincide término a término y gestiona el caso de rectas paralelas
(denominador nulo). Correcto.

## 5. Cálculo de la corrección del TCP
Es la parte central y la he derivado desde cero. En el instante de la medida la brida es
fija; con el TCP de referencia activo se cumple pSearch = F · refTCP, de donde
F = pSearch · refTCP⁻¹. Buscamos el TCP corregido T que sitúe la herramienta en el centro
enseñado pRef, es decir F · T = pRef, lo que da:

    T = refTCP · pSearch⁻¹ · pRef

El script calcula exactamente esa expresión. La formulación es correcta.

## 6. Búsqueda en Z
Una vez centrado en XY, el algoritmo desciende hasta que ambas entradas se desactivan y
vuelve a ascender hasta reactivarlas, capturando la pose en el plano de los haces, que
fija la referencia en Z. La geometría es correcta; los signos de dirección dependen del
montaje y se resuelven con un parámetro de configuración.

## 7. Corrección angular (RX/RY)
Repitiendo el sondeo a distinta altura, la inclinación de la herramienta se obtiene
descomponiendo el desplazamiento de la intersección frente a la Z mediante atan2(x,z) y
atan2(y,z). El proceso es iterativo y converge a la precisión solicitada, por lo que la
aproximación de ángulo pequeño no compromete el resultado. Correcto.

## Conclusión
Toda la formulación es geometría de cuerpo rígido estándar (transformadas homogéneas), una
intersección de rectas 2D de libro y un ajuste angular iterativo. No hay estimación
numérica delicada ni optimización que requiera un perfil matemático especializado. La
verificación de la corrección del TCP y de la intersección confirma que el cálculo es
correcto. El trabajo restante es de puesta en marcha y ajuste en hardware (velocidad de
sondeo, detección del hilo y convenciones de signo del montaje), no matemático.

La única observación, que no afecta a la corrección sino a la repetibilidad, es que el
método usa el número mínimo de puntos (dos por haz, recta exacta sin promediado). Si la
repetibilidad en hardware no fuera suficiente, la mejora es estándar y acotada: tomar más
cruces y ajustar las rectas por mínimos cuadrados. Es una optimización opcional de
precisión, no un requisito para el funcionamiento.
