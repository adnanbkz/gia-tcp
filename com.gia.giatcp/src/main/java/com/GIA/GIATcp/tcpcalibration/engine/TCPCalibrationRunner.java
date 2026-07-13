package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.math.TCPCalibrationMaths;
import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

/**
 * Runs the calibration sequence. ALL geometry is done in {@link TCPCalibrationMaths};
 * the robot only does the realtime probing, abstracted behind a {@link ProbeTransport}
 * (live secondary-interface send, or the runtime callback server). Nothing UR-specific
 * lives in the maths, so the same flow ports to other robots (e.g. Estun) by swapping
 * the transport + script. The caller supplies the transport (no default), so the engine
 * never depends on a concrete adapter.
 */
public final class TCPCalibrationRunner {

	private final ProbeTransport transport;

	/** Runner over an explicit transport (live secondary, or the runtime server). */
	public TCPCalibrationRunner(ProbeTransport transport) {
		this.transport = transport;
	}

	/** Runs a full XYZ (+ optional orientation) calibration. Call off the UI thread. */
	public TCPCalibrationResult calibrate(TCPCalibrationSpec s) {
		// 1. XY probe around the taught centre (CAPTRON pStart). The correction reference
		// (s.pRef, CAPTRON h()) is only compared against, never probed around.
		double[] pStart = s.pStart != null ? s.pStart : s.pRef;
		CircleData c = transport.probeCircle(pStart, s);
		if (c == null) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_ROBOT_REPLY);
		}
		if (!c.valid()) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_INTERSECT);
		}
		TCPCalibrationMaths.CenterResult centre =
				TCPCalibrationMaths.computeCenter(c.poses, pStart, s.radiusMm / 1000.0);
		if (centre.error != TCPCalibrationMaths.OK) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_INTERSECT);
		}

		// 2. Z search at the true centre. Failures carry the robot-reported reason so a bad
		// teach/TCP (input low at the intersect) reads differently from a failed retract
		// or immerse; a missing reply keeps the legacy SEARCH_Z_FAILED.
		ZSearchResult z = transport.searchZ(centre.intersect, s);
		if (z == null) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.SEARCH_Z_FAILED);
		}
		if (z.pose == null) {
			return TCPCalibrationResult.error(z.failure != null
					? z.failure : TCPCalibrationResult.Status.SEARCH_Z_FAILED);
		}
		double[] pSearchZ = z.pose;

		// 3. Correction (all maths in Java). A referencing run RE-BASES: the measured pose
		// is the new baseline, so its XYZ correction is identity by construction (CAPTRON
		// stores h() first and computes against it) — the taught centre's teach error must
		// never survive as a stored/applied correction. Any angle measured below is a real
		// tool property and still applies.
		double[] correction = s.referenceRun
				? new double[6]
				: TCPCalibrationMaths.correction(s.pRef, pSearchZ);
		double[] correctedTcp = TCPCalibrationMaths.correctedTcp(s.refTcp, correction);
		double diameterMm = centre.diameterM * 1000.0 + s.diamOffsetMm;

		// XYZ band: asymmetric Min/Max when provided (CAPTRON), else the symmetric +/- band.
		boolean okXYZ = s.tolMinXYZm != null && s.tolMaxXYZm != null
				? TCPCalibrationMaths.withinTolAsym(correction, s.tolMinXYZm, s.tolMaxXYZm)
				: TCPCalibrationMaths.withinTol(correction, s.tolXYZm);
		TCPCalibrationResult.Status status = okXYZ
				? TCPCalibrationResult.Status.OK : TCPCalibrationResult.Status.OUT_OF_TOLERANCE;
		// Diameter band (CAPTRON parity): a wrong/bent tool can pass XYZ and still probe a
		// diameter far off the expected one. Only checked when nominal and band are known.
		if (status == TCPCalibrationResult.Status.OK && s.diamNominalMm > 0) {
			double dev = diameterMm - s.diamNominalMm;
			boolean asymDiam = s.diamTolMinMm != 0 || s.diamTolMaxMm != 0;
			boolean outDiam = asymDiam
					? dev < s.diamTolMinMm || dev > s.diamTolMaxMm
					: s.diamTolMm > 0 && !TCPCalibrationMaths.withinTolVal(diameterMm, s.diamNominalMm, s.diamTolMm);
			if (outDiam) {
				status = TCPCalibrationResult.Status.OUT_OF_TOLERANCE;
			}
		}

		// 4. Optional orientation: try a higher circle first, then a lower one.
		if (s.adjustAngle && status == TCPCalibrationResult.Status.OK) {
			double[] angle = measureOrientation(s);
			if (angle == null) {
				return TCPCalibrationResult.error(TCPCalibrationResult.Status.ORIENTATION_NOT_POSSIBLE);
			}
			if (Math.abs(Math.toDegrees(angle[0])) > s.maxAngleRxDeg
					|| Math.abs(Math.toDegrees(angle[1])) > s.maxAngleRyDeg) {
				return TCPCalibrationResult.error(TCPCalibrationResult.Status.ORIENTATION_NOT_POSSIBLE);
			}
			correctedTcp = TCPCalibrationMaths.poseTrans(correctedTcp,
					new double[] { 0, 0, 0, angle[0], angle[1], 0 });
			correction[3] += angle[0];
			correction[4] += angle[1];
		}

		return new TCPCalibrationResult(status, correctedTcp, correction, diameterMm, pSearchZ);
	}

	/**
	 * Orientation tilt [RX, RY] (rad), or {@code null} = NOT POSSIBLE. Probes a circle
	 * RAISED in base +Z first (torch up); if it does not cross the beams, a LOWERED one;
	 * if neither crosses, the calibration is not possible.
	 */
	private double[] measureOrientation(TCPCalibrationSpec s) {
		double dz = s.orientationDzMm / 1000.0;
		double[] angle = angleAt(offsetBaseZ(s.pRef, dz), s);   // higher
		if (angle == null) {
			angle = angleAt(offsetBaseZ(s.pRef, -dz), s);       // lower
		}
		return angle;
	}

	private double[] angleAt(double[] probePose, TCPCalibrationSpec s) {
		CircleData c = transport.probeCircle(probePose, s);
		if (c == null || !c.valid()) {
			return null;
		}
		TCPCalibrationMaths.CenterResult centre =
				TCPCalibrationMaths.computeCenter(c.poses, probePose, s.radiusMm / 1000.0);
		if (centre.error != TCPCalibrationMaths.OK) {
			return null;
		}
		return TCPCalibrationMaths.calcAngleXY(s.pRef, centre.intersect);
	}

	private static double[] offsetBaseZ(double[] pose, double dz) {
		return new double[] { pose[0], pose[1], pose[2] + dz, pose[3], pose[4], pose[5] };
	}
}
