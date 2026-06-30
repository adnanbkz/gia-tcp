package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.math.TCPCalibrationMaths;
import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;

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
		// 1. XY probe around the taught centre.
		CircleData c = transport.probeCircle(s.pRef, s);
		if (c == null) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_ROBOT_REPLY);
		}
		if (!c.valid()) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_INTERSECT);
		}
		TCPCalibrationMaths.CenterResult centre =
				TCPCalibrationMaths.computeCenter(c.poses, s.pRef, s.radiusMm / 1000.0);
		if (centre.error != TCPCalibrationMaths.OK) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_INTERSECT);
		}

		// 2. Z search at the true centre.
		double[] pSearchZ = transport.searchZ(centre.intersect, s);
		if (pSearchZ == null) {
			return TCPCalibrationResult.error(TCPCalibrationResult.Status.SEARCH_Z_FAILED);
		}

		// 3. Correction (all maths in Java).
		double[] correction = TCPCalibrationMaths.correction(s.pRef, pSearchZ);
		double[] correctedTcp = TCPCalibrationMaths.correctedTcp(s.refTcp, correction);
		double diameterMm = centre.diameterM * 1000.0 + s.diamOffsetMm;

		TCPCalibrationResult.Status status = TCPCalibrationMaths.withinTol(correction, s.tolXYZm)
				? TCPCalibrationResult.Status.OK : TCPCalibrationResult.Status.OUT_OF_TOLERANCE;

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

		return new TCPCalibrationResult(status, correctedTcp, correction, diameterMm);
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
