# Instalación y build — GIA TCP Calibrator

URCap (bundle OSGi) para UR e-Series. JDK 8 + Maven. `groupId=com.GIA`, `artifactId=GIATcp`,
`version=1.0`, `symbolicName=com.GIA.GIATcp`.

## Artefactos generados

`mvn package` (o cualquier `install`) deja en `target/`:

| Archivo | Qué es |
|---|---|
| `target/GIATcp-1.0.jar` | Bundle OSGi (`${finalName}` = `artifactId-version`). Es lo que se despliega. |
| `target/GIA-TCP-1.0.urcap` | Copia del jar con extensión `.urcap`, para instalar desde la UI de PolyScope. |

Los perfiles `install` copian el **jar** renombrado a `${symbolicname}.jar` = `com.GIA.GIATcp.jar`
dentro del directorio `.urcaps/` del destino.

## Comandos

Ejecutar siempre desde la raíz del módulo: `.../giatcp/com.gia.giatcp/`.

### Iterar / verificar

```bash
mvn -q -DskipTests compile        # compilación rápida (loop de desarrollo)
mvn clean                         # borra target/
mvn test                          # todos los tests (JUnit 5, surefire)
mvn test -Dtest=TCPCalibrationMathsTest            # una clase
mvn test -Dtest=TCPCalibrationMathsTest#nombreTest # un método
mvn package                       # genera target/GIATcp-1.0.jar + .urcap (sin desplegar)
mvn clean package                 # build limpio completo
```

### Desplegar — perfiles `install`

Cada perfil corre en la fase `install`. El jar se copia como `com.GIA.GIATcp.jar`.

```bash
# Host PolyScope local → ~/.urcaps/
mvn install -Plocal

# UR Sim local → ${ursim.home}/.urcaps/   (ursim.home en pom.xml: /opt/ursim-proves/ursim-5.22.0.1214828)
mvn install -Pursim

# Robot físico → root@192.168.0.3:/root/.urcaps/  + reinicia la UI (pkill java)
mvn install -Premote
```

> `-Premote` usa `sshpass`/`scp` con host/usuario/contraseña del bloque `<properties>` del `pom.xml`
> (`urcap.install.host` = `192.168.0.3`, user `root`, pass `1`). Tras copiar hace
> `ssh ... pkill java` → **reinicia PolyScope entero** (la UI cae y vuelve sola; espera ~1 min).

Saltar tests en el deploy:

```bash
mvn install -Premote -DskipTests
```

### Instalación manual por UI (alternativa a `-Premote`)

1. `mvn package` → coge `target/GIA-TCP-1.0.urcap`.
2. Cópialo a un USB o a la máquina del robot.
3. PolyScope → **Settings → System → URCaps → +** → selecciona el `.urcap`.
4. **Restart** cuando lo pida (instala/actualiza en `.urcaps/`).

## Ajustar destino

En `<properties>` del `pom.xml`:

```xml
<urcap.install.host>192.168.0.3</urcap.install.host>   <!-- IP del robot para -Premote -->
<urcap.install.username>root</urcap.install.username>
<urcap.install.password>1</urcap.install.password>
<ursim.home>/opt/ursim-proves/ursim-5.22.0.1214828</ursim.home>  <!-- destino -Pursim -->
```

## Requisitos

- JDK 8 (`source/target = 1.8`).
- Maven 3.6+, `sshpass` y `scp` en PATH (solo para `-Premote`).
- UR API (`com.ur.urcap:api`) y log4j son `provided`: el controlador los aporta en runtime;
  no van dentro del bundle.

## Referenciado / calibración en robot real (importante)

El botón **Iniciar referenciado** (wizard) y **Calibrar (test)** (Overview) inyectan el
programa por el interface **primary/secondary** (30001/30002). En e-Series, con el robot en
modo **Local** el controlador **ignora** ese script: el brazo no se mueve y la operación
acaba en timeout. **Requisitos:**

- **Remote Control activado** (menú arriba a la derecha en PolyScope). La URCap ahora consulta
  el Dashboard (`is in remote control`, puerto 29999) antes de lanzar; si estás en Local, avisa
  al instante en vez de esperar 120 s.
- Brazo **encendido y frenos liberados**.
- Sensores en **dark-operate** (haz cortado → 24 V); validar en *Diagnostics*.
- TCP de referencia existente y resoluble (si lo borras/renombras, la URCap lo detecta y avisa).

Si algo falla, sale un **aviso con el motivo** (sin respuesta, búsqueda Z fallida, fuera de
tolerancia, etc.). Los popups dependen del ajuste *Gestión de errores → abrir diálogos*.

### Referenciar en modo Local (sin Remote Control) — recomendado

Como el botón de instalación inyecta script (necesita Remote Control), para referenciar en
**Local** se usa el **nodo de programa**, que se mueve cuando le das **Play** desde el pendant:

1. Configura el TCP en el wizard (variante, entradas, TCP de referencia, **enseñar centro**).
   Enseñar el centro usa el diálogo de mover-robot, que ya es manual/Local.
2. En un programa, añade el nodo **GIA TCP**, selecciona el TCP y marca
   **"Guardar como referencia de instalación"**.
3. **Play.** El robot baja al centro, hace el círculo de sondeo + búsqueda Z y, al terminar,
   la URCap **guarda la corrección en la instalación** (igual que el botón de referenciado,
   pero sin Remote Control). El badge/lectura del Overview se actualizan solos.

Tras eso, *Check/Validate* del nodo ya funcionan contra esa referencia guardada. El
referenciado usa tolerancias anchas: mide y guarda, no falla por banda.

## Verificar despliegue

```bash
# robot físico
sshpass -p 1 ssh root@192.168.0.3 ls -la /root/.urcaps/

# log del controlador (errores de arranque del bundle)
sshpass -p 1 ssh root@192.168.0.3 tail -f /root/log_history.txt
```

Tras instalar, comprobar en PolyScope que aparece el nodo de instalación **GIA TCP** y el nodo
de programa de calibración. Puertos loopback usados dentro del controlador:

- `127.0.0.1:5512` — servidor de calibración runtime del **nodo de programa** (`CalibrationServer.PORT`).
- `127.0.0.1:5510` — retorno de la calibración lanzada desde **Installation** (`Const.CALIB_RETURN_PORT`).
