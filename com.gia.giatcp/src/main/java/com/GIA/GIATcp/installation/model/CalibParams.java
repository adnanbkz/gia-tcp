package com.GIA.GIATcp.installation.model;

import com.GIA.GIATcp.util.Const;

/**
 * Calibration motion parameters for one TCP, as taught in step 5 of the setup
 * wizard. Lengths are millimetres, speeds mm/s, accelerations mm/s^2, angles degrees.
 */
public class CalibParams {

	public double radiusMm = Const.DEF_RADIUS_MM;
	public double speedMmS = Const.DEF_SPEED_MM_S;
	public double accelMmS2 = Const.DEF_ACCEL_MM_S2;
	public double overrunDeg = Const.DEF_OVERRUN_DEG;
	public double searchZMm = Const.DEF_SEARCHZ_MM;
	public boolean invertZ = Const.DEF_INVERTZ;
	public double realDiameterMm = Const.DEF_REALDIAM_MM;

	public boolean adjustAngle = Const.DEF_ADJANGLE;
	public int iterator = Const.DEF_ITER;
	public double offsetZMm = Const.DEF_OFFZ_MM;
	public double accuracyDeg = Const.DEF_ACCURACY_DEG;
	public double maxAngleRxDeg = Const.DEF_MAXANGLE_DEG;
	public double maxAngleRyDeg = Const.DEF_MAXANGLE_DEG;

	/** Signed search-Z stroke as passed to the script, following CAPTRON's invert-Z convention. */
	public double signedSearchZMm() {
		return signedZ(Math.abs(searchZMm));
	}

	/** Signed safe approach offset: same direction as the search retract. */
	public double signedApproachZMm(double approachMm) {
		return signedZ(-Math.abs(approachMm));
	}

	/** Signed immerse offset: opposite to the search retract, back through the beam plane. */
	public double signedImmerseZMm(double immerseMm) {
		return signedZ(Math.abs(immerseMm));
	}

	private double signedZ(double valueMm) {
		return invertZ ? -valueMm : valueMm;
	}
}
