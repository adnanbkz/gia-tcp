package com.GIA.GIATcp.installation.model;

/**
 * In-memory representation of one configured GIA TCP slot. Persistence to/from the
 * installation DataModel is handled by {@link TcpStore}.
 */
public class GiaTcp {

	/** 1-based slot id (1..30). */
	public int id;
	public boolean exists;
	public String name;
	public TcpVariant variant = TcpVariant.UNKNOWN;

	/** Input codes: 0..7 = standard digital_in[n], 10..17 = config_in[n-10]. */
	public int ioX = -1;
	public int ioY = -1;

	/** Display name of the reference TCP this calibration is relative to. */
	public String refTcp = "";

	/** Taught centre pose in base frame, SI units (m, rad), length 6. */
	public double[] centerPose = new double[6];

	public boolean calibrated;
	/** TCP correction relative to the reference TCP, SI units (m, rad), length 6. */
	public double[] correction = new double[6];
	/** Measured tool diameter in mm. */
	public double diameterMm;

	public CalibParams params = new CalibParams();

	public boolean isReadyForCalibration() {
		return exists && variant.isSelectable() && ioX >= 0 && ioY >= 0 && ioX != ioY
				&& refTcp != null && !refTcp.isEmpty() && isCenterTaught();
	}

	public boolean isCenterTaught() {
		for (double v : centerPose) {
			if (v != 0.0) {
				return true;
			}
		}
		return false;
	}
}
