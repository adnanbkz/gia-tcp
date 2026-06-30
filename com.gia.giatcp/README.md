# GIA TCP Calibrator (URCap)

A GIA ROBOTICS Universal Robots URCap that checks, validates and recalibrates a tool
centre point (TCP) using a **light-barrier** TCP measurement station. No external licence
dependency.

## Hardware

- **2 × SensoPart FGL 50-IK-50-PS-M4** infrared fork sensors (order no. 832-11022),
  mounted **crossed at ~90°** with coplanar beams, forming a "+" the tool enters from above.
- Each sensor: PNP digital output, M8 4-pin → two UR digital inputs.
- Both sensors set to **dark-operate** (output active = beam blocked), because the algorithm
  treats *digital input HIGH = beam interrupted*.
- See `docs/montaje_calibrador_2sensores.md` for the full mounting & wiring spec.

## Features

- **Installation page "GIA TCP Calibrator"**
  - Overview: manage up to 30 TCPs (add / remove / rename / select), TCP correction readout,
    CALIBRATED badge, manual Calibrate / Stop.
  - Setup Wizard (7 steps): TCP variant → input X/Y → reference TCP → teach centre pose →
    parameters → referencing motion → done.
  - **Diagnostics** (read-only): live digital-input indicators + live TCP pose, edge log while
    you sweep the tool through the beams, Move-to-center, and a **Repeatability test**
    (N probes, mean/sigma/range, measure-only).
  - Settings: error handling mode + log debug level.
- **Program node "GIA TCP Action"**
  - Actions: TCP Check, TCP Validate, TCP Recalibrate.
  - Tabs: Basic Settings, Tolerances, Assignment.
  - Auto-inserted "If Error" child node for error handling.

## Architecture notes

- The whole calibration algorithm runs in URScript (`src/main/resources/scripts`,
  functions `gia_tcp_*` / `gia__*`): a circular probe around the taught centre detects the
  two beam-interrupt chords, intersects them (`gia__calc2DIntersect`) for the XY centre, then
  searches Z. No embedded RPC server is used — the line intersection is pure URScript.
- **Live motion from the installation page** (calibration, repeatability) is sent to the
  robot's **primary interface (port 30001)** as a program; the program streams its result back
  over a loopback socket (port 5510). Motion cannot run as a secondary program, hence primary.
- **Live state reads** for Diagnostics are non-intrusive: digital inputs via the IO API
  (`DigitalIO.getValue()`); TCP pose via the realtime interface (port 30003,
  `RobotRealtimeReader`). No program runs while monitoring.
- **Program-node executions** use the standard URCap `generateScript`, calling the `gia_tcp_*`
  library the installation node contributes as a preamble.

## Build

```
mvn clean package                # -> target/GIA-TCP-1.0.urcap
mvn install -Pursim              # copy jar into ${ursim.home}/.urcaps
mvn install -Premote             # scp to a real robot (set urcap.install.host/password)
```

Requires the UR Cap API 1.17.0 in the local Maven repo (same as the GIAWeld project).
Builds run offline (`mvn -o ...`) from the cached dependencies.

## Hardware-test checklist (real sensor required)

The UR simulator cannot trigger the light barriers, so end-to-end calibration must be
verified on a robot with the two physical sensors:

1. Wire each sensor output to a digital input; set both to dark-operate; note the input names.
2. **Diagnostics**: sweep the tool into each beam and confirm the matching input goes
   **HIGH/green** (if inverted, flip the sensor N.O./N.C.). Set the UR input filter to minimum.
3. Setup wizard: pick the X and Y inputs, a reference TCP (activate it on the robot first),
   jog so both beams are interrupted and press **Set Center**, set radius / speed / search Z.
4. **Start Referencing** — the robot probes the sensors and reports a correction.
5. **Diagnostics → Repeatability test** (e.g. 10 runs): confirm the 1-sigma spread is small
   (sub-0.1 mm target); lower the probe speed if needed. Repeatability ≠ accuracy — validate
   accuracy against an independent reference.
6. In a program, add **GIA TCP Action → TCP Recalibrate** and confirm it reproduces the
   correction and writes `giaActionTCP` (or your custom variable).

## Follow-ups / out of scope (v1)

- Metrology-grade edge capture is already in URScript (control-loop rate); the Diagnostics
  edge log is a few-Hz wiring/sanity aid, not metrology.
- "Try again x times" retry loop wiring on the If-Error node.
- RX/RY angle adjustment: leave disabled until XYZ is validated on hardware.
- Localisations beyond English; pendant on-screen-keyboard polish.
- Tuning default parameters for the FGL 50 (beam width, probe speed).
