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

	/**
	 * Parses the CSV line "status,xMM,yMM,zMM,rxDeg,ryDeg,rzDeg,diamMM". The reporting
	 * script always sends all 8 fields (also on error statuses), so anything shorter or
	 * non-finite is a truncated/foreign line and must NOT count as a received result — a
	 * bare "0" would otherwise read as a successful referencing with a zero correction.
	 */
	public static CalibrationResult parse(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return timeout();
		}
		String[] p = csv.trim().split(",");
		if (p.length < 8) {
			return timeout();
		}
		try {
			double[] raw = new double[8];
			for (int i = 0; i < 8; i++) {
				raw[i] = Double.parseDouble(p[i].trim());
				if (Double.isNaN(raw[i]) || Double.isInfinite(raw[i])) {
					return timeout();
				}
			}
			int status = (int) Math.round(raw[0]);
			double[] corr = {
					raw[1] / 1000.0, raw[2] / 1000.0, raw[3] / 1000.0,
					Math.toRadians(raw[4]), Math.toRadians(raw[5]), Math.toRadians(raw[6])
			};
			return new CalibrationResult(true, status, corr, raw[7]);
		} catch (RuntimeException e) {
			return timeout();
		}
	}
}
