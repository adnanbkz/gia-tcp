package com.GIA.GIATcp.tcpcalibration.model;

/**
 * Inputs for one calibration run. All poses are base-frame SI (m, rad); distances in mm,
 * speeds in SI (m/s, m/s²), angles in degrees where noted. Robot-agnostic on purpose.
 */
public final class TCPCalibrationSpec {

	/**
	 * Correction reference pose (base frame, SI): the pose measured at referencing when the
	 * TCP has been referenced (CAPTRON h()), else the taught centre. The correction is the
	 * pose delta between this and the measured beam-plane pose.
	 */
	public double[] pRef;
	/**
	 * Probe start pose (base frame, SI): the taught centre the circle runs around (CAPTRON
	 * j()/pStart). Null = fall back to {@link #pRef} (both are the same until referenced).
	 */
	public double[] pStart;
	/** Reference TCP offset (SI) active during probing. */
	public double[] refTcp;

	public int in1;
	public int in2;

	public double radiusMm = 12.0;
	public double accMs2 = 0.5;
	public double velMs = 0.02;
	public double overrunDeg = 15.0;

	public double zSearchMm = 10.0;
	public double zImmerseMm = 0.0;

	/** XYZ tolerance band (m): the correction must stay within +/- these. */
	public double[] tolXYZm = { 0.999, 0.999, 0.999 };
	/**
	 * Asymmetric XYZ band (m, CAPTRON Min/Max): correction must sit in [min[i], max[i]].
	 * Null = fall back to the symmetric {@link #tolXYZm}.
	 */
	public double[] tolMinXYZm;
	public double[] tolMaxXYZm;
	public double diamOffsetMm = 0.0;
	/** Expected tool diameter (mm): real diameter if configured, else the referenced one. */
	public double diamNominalMm = 0.0;
	/** +/- band (mm) around {@link #diamNominalMm}; 0 disables the diameter check. */
	public double diamTolMm = 0.0;
	/** Asymmetric diameter band (mm) around nominal; both 0 = use the symmetric band. */
	public double diamTolMinMm = 0.0;
	public double diamTolMaxMm = 0.0;

	public boolean adjustAngle = false;
	/** How much to raise (+) / lower (-) the orientation circle vs the XY plane (mm). */
	public double orientationDzMm = 5.0;
	public double maxAngleRxDeg = 10.0;
	public double maxAngleRyDeg = 10.0;

	/** 1-based TCP slot this run belongs to (0 = unknown). Used to persist the result. */
	public int tcpId = 0;
	/**
	 * When true, a successful run is written back to the installation store for {@link #tcpId}.
	 * This is how "referencing" works in Local mode: the program node (run with Play) measures and
	 * the {@link CalibrationServer} persists the baseline — no Remote Control / script injection.
	 */
	public boolean persistToInstallation = false;
}
