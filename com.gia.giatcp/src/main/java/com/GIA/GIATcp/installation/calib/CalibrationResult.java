package com.GIA.GIATcp.installation.calib;

/**
 * Outcome of a live calibration / referencing run reported back by the robot.
 * Correction is stored in SI units (m, rad) ready for the data model; diameter in mm.
 */
public class CalibrationResult {

	public final boolean received;
	public final int status;
	public final double[] correctionSi; // length 6: x,y,z (m), rx,ry,rz (rad)
	public final double diameterMm;

	private CalibrationResult(boolean received, int status, double[] correctionSi, double diameterMm) {
		this.received = received;
		this.status = status;
		this.correctionSi = correctionSi;
		this.diameterMm = diameterMm;
	}

	public boolean isSuccess() {
		return received && status == 0;
	}

	/**
	 * Localization key describing this run for the UI. A missing reply is reported
	 * as a no-response/timeout; otherwise the robot-reported status code
	 * ({@code gia__actionStatus}, see inst.script) is mapped to a human message.
	 */
	public String statusKey() {
		if (!received) {
			return "REF_NO_REPLY";
		}
		switch (status) {
			case 0:   return "STATUS_CALIB_OK";
			case -1:  return "REF_ERR_1";
			case -2:  return "REF_ERR_2";
			case -3:  return "REF_ERR_3";
			case -4:  return "REF_ERR_4";
			case -5:  return "REF_ERR_5";
			case -6:  return "REF_ERR_6";
			case -7:  return "REF_ERR_7";
			case -11: return "REF_ERR_11";
			case -12: return "REF_ERR_12";
			case -13: return "REF_ERR_13";
			case -14: return "REF_ERR_14";
			case -21: return "REF_ERR_21";
			case -31: return "REF_ERR_31";
			default:  return "REF_ERR_UNKNOWN";
		}
	}

	public static CalibrationResult timeout() {
		return new CalibrationResult(false, 999, new double[6], 0.0);
	}

	/** Parses the CSV line "status,xMM,yMM,zMM,rxDeg,ryDeg,rzDeg,diamMM". */
	public static CalibrationResult parse(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return timeout();
		}
		String[] p = csv.trim().split(",");
		try {
			int status = (int) Math.round(Double.parseDouble(p[0].trim()));
			double[] corr = new double[6];
			if (p.length >= 7) {
				corr[0] = Double.parseDouble(p[1].trim()) / 1000.0;
				corr[1] = Double.parseDouble(p[2].trim()) / 1000.0;
				corr[2] = Double.parseDouble(p[3].trim()) / 1000.0;
				corr[3] = Math.toRadians(Double.parseDouble(p[4].trim()));
				corr[4] = Math.toRadians(Double.parseDouble(p[5].trim()));
				corr[5] = Math.toRadians(Double.parseDouble(p[6].trim()));
			}
			double diam = p.length >= 8 ? Double.parseDouble(p[7].trim()) : 0.0;
			return new CalibrationResult(true, status, corr, diam);
		} catch (RuntimeException e) {
			return timeout();
		}
	}
}
