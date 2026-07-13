package com.GIA.GIATcp.tcpcalibration.model;

/** Outcome of a calibration run: a status plus the computed TCP correction (when OK). */
public final class TCPCalibrationResult {

	/**
	 * Ordinals are wire protocol: the runtime server sends {@code status.ordinal()} to the
	 * robot and the generated URScript compares the raw numbers (0 = OK, 4 = OUT_OF_TOLERANCE,
	 * and the light check returns 6/7 directly). Only append values, never reorder.
	 */
	public enum Status {
		OK,
		NO_ROBOT_REPLY,            // no socket reply (sim / wiring / not connected)
		NO_INTERSECT,              // beam lines (near) parallel — no clean intersection
		SEARCH_Z_FAILED,           // Z retract never freed the beams (search motion failed)
		OUT_OF_TOLERANCE,          // correction outside the XYZ band
		ORIENTATION_NOT_POSSIBLE,  // neither the higher nor the lower circle crossed the beams
		INPUT_LOW_AT_CENTER,       // tool does not cut both beams at the intersect (CAPTRON 4/21: bad teach/TCP/tool)
		IMMERSE_FAILED,            // immerse never re-cut both beams (CAPTRON 31: tool worn/missing)
		WRONG_POINT_COUNT,         // not exactly 4 edges per beam (chatter / overrun too small, CAPTRON 11)
		INTERSECT_TOO_FAR,         // intersection outside the probe radius (bad teach, CAPTRON 13)
		PERSIST_FAILED             // measured OK but the referencing could not be saved to the installation
	}

	public final Status status;
	public final double[] correctedTcp; // SI, null unless OK/OUT_OF_TOLERANCE
	public final double[] correction;   // SI [x,y,z,rx,ry,rz], null unless computed
	public final double diameterMm;
	/** Measured beam-plane pose (base frame, SI), null unless measured. Referencing stores it as the reference pose. */
	public final double[] measuredPose;

	public TCPCalibrationResult(Status status, double[] correctedTcp, double[] correction, double diameterMm,
			double[] measuredPose) {
		this.status = status;
		this.correctedTcp = correctedTcp;
		this.correction = correction;
		this.diameterMm = diameterMm;
		this.measuredPose = measuredPose;
	}

	public boolean isOk() {
		return status == Status.OK;
	}

	public static TCPCalibrationResult error(Status status) {
		return new TCPCalibrationResult(status, null, null, 0, null);
	}
}
