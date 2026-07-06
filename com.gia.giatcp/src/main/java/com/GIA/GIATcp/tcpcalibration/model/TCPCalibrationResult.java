package com.GIA.GIATcp.tcpcalibration.model;

/** Outcome of a calibration run: a status plus the computed TCP correction (when OK). */
public final class TCPCalibrationResult {

	public enum Status {
		OK,
		NO_ROBOT_REPLY,            // no socket reply (sim / wiring / not connected)
		NO_INTERSECT,              // circle did not cross both beams cleanly
		SEARCH_Z_FAILED,           // Z search/immerse never reached the beam plane
		OUT_OF_TOLERANCE,          // correction outside the XYZ band
		ORIENTATION_NOT_POSSIBLE   // neither the higher nor the lower circle crossed the beams
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
