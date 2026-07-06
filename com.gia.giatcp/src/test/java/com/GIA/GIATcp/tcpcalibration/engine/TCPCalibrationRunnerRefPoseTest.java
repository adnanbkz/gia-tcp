package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAPTRON reference semantics: the circle probes around the taught centre (pStart, j())
 * while the correction is measured against the referenced pose (pRef, h()).
 */
class TCPCalibrationRunnerRefPoseTest {

	/** Same synthetic geometry as the diameter test, but records the circle start pose. */
	private static final class RecordingTransport implements ProbeTransport {

		double[] circleStart;

		@Override
		public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
			circleStart = pStart;
			double[][] poses = {
					pose(0.010, -0.002), pose(0.010, 0.002),
					pose(-0.010, -0.002), pose(-0.010, 0.002),
					pose(-0.002, 0.010), pose(0.002, 0.010),
					pose(-0.002, -0.010), pose(0.002, -0.010)
			};
			return new CircleData(4, 4, poses);
		}

		@Override
		public double[] searchZ(double[] pCentre, TCPCalibrationSpec s) {
			return new double[] { 0, 0, -0.005, 0, 0, 0 };
		}

		private static double[] pose(double x, double y) {
			return new double[] { x, y, 0, 0, 0, 0 };
		}
	}

	@Test
	void circleRunsAroundStartPoseNotTheReference() {
		RecordingTransport t = new RecordingTransport();
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pStart = new double[] { 0.001, 0.002, 0, 0, 0, 0 };
		s.pRef = new double[] { 0, 0, -0.005, 0, 0, 0 };
		s.refTcp = new double[6];
		new TCPCalibrationRunner(t).calibrate(s);
		assertArrayEquals(s.pStart, t.circleStart, 1e-12);
	}

	@Test
	void correctionIsMeasuredAgainstTheReferencePose() {
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pStart = new double[] { 0.001, 0.002, 0, 0, 0, 0 };
		s.pRef = new double[] { 0, 0, -0.003, 0, 0, 0 }; // 2 mm off the measured plane
		s.refTcp = new double[6];
		TCPCalibrationResult r = new TCPCalibrationRunner(new RecordingTransport()).calibrate(s);
		assertEquals(TCPCalibrationResult.Status.OK, r.status);
		assertEquals(0.002, r.correction[2], 1e-9);
		assertArrayEquals(new double[] { 0, 0, -0.005, 0, 0, 0 }, r.measuredPose, 1e-12);
	}

	@Test
	void missingStartPoseFallsBackToTheReference() {
		RecordingTransport t = new RecordingTransport();
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pRef = new double[] { 0, 0, -0.005, 0, 0, 0 };
		s.refTcp = new double[6];
		new TCPCalibrationRunner(t).calibrate(s);
		assertArrayEquals(s.pRef, t.circleStart, 1e-12);
	}
}
