package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each geometric failure keeps its own status (the legacy stack distinguishes -1/-2/-3
 * too): wrong edge count, parallel beam lines and an intersection outside the probe
 * radius need different remedies (overrun/speed, sensor geometry, re-teach).
 */
class TCPCalibrationRunnerStatusTest {

	/** Fake transport built from a fixed CircleData reply. */
	private static final class FixedTransport implements ProbeTransport {

		private final CircleData reply;

		FixedTransport(CircleData reply) {
			this.reply = reply;
		}

		@Override
		public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
			return reply;
		}

		@Override
		public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
			return ZSearchResult.ok(new double[6]);
		}
	}

	private static TCPCalibrationSpec spec() {
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pRef = new double[6];
		s.refTcp = new double[6];
		return s;
	}

	private static double[] pose(double x, double y) {
		return new double[] { x, y, 0, 0, 0, 0 };
	}

	private static double[][] crossPoses(double cx) {
		return new double[][] {
				pose(cx + 0.010, -0.002), pose(cx + 0.010, 0.002),
				pose(cx - 0.010, -0.002), pose(cx - 0.010, 0.002),
				pose(cx - 0.002, 0.010), pose(cx + 0.002, 0.010),
				pose(cx - 0.002, -0.010), pose(cx + 0.002, -0.010)
		};
	}

	@Test
	void wrongEdgeCountHasItsOwnStatus() {
		TCPCalibrationResult r = new TCPCalibrationRunner(
				new FixedTransport(new CircleData(3, 4, crossPoses(0)))).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.WRONG_POINT_COUNT, r.status);
	}

	@Test
	void parallelBeamLinesStayNoIntersect() {
		// Both beams' crossing midpoints lie on parallel lines (y=0 and y=0.002).
		double[][] parallel = {
				pose(0.010, -0.002), pose(0.010, 0.002),
				pose(-0.010, -0.002), pose(-0.010, 0.002),
				pose(0.010, 0.000), pose(0.010, 0.004),
				pose(-0.010, 0.000), pose(-0.010, 0.004)
		};
		TCPCalibrationResult r = new TCPCalibrationRunner(
				new FixedTransport(new CircleData(4, 4, parallel))).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.NO_INTERSECT, r.status);
	}

	@Test
	void intersectOutsideTheProbeRadiusHasItsOwnStatus() {
		// Cross centred 50 mm away from the probe start; default radius is 12 mm.
		TCPCalibrationResult r = new TCPCalibrationRunner(
				new FixedTransport(new CircleData(4, 4, crossPoses(0.05)))).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.INTERSECT_TOO_FAR, r.status);
	}
}
