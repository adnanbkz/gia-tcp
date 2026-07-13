package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The orientation circles must anchor on the pose just measured at the beam plane
 * (pSearchZ), never on the reference pose: both measurements share the same TCP error,
 * so their difference isolates the tilt. Anchored on pRef, 1 mm of XYZ drift at
 * dz = 5 mm would read as a false 11.3° tilt (> 10° max => ORIENTATION_NOT_POSSIBLE
 * for a perfectly straight tool).
 */
class TCPCalibrationRunnerOrientationTest {

	/**
	 * Tool whose axis crosses the horizontal plane at height z at
	 * {@code x = 0.001 + slope * (z + 0.005)} (1 mm X drift vs the reference; the beam
	 * plane sits at z = -0.005). slope = tan(tilt); 0 = perfectly straight.
	 */
	private static final class DriftedToolTransport implements ProbeTransport {

		final double slope;
		final List<double[]> probeStarts = new ArrayList<double[]>();

		DriftedToolTransport(double slope) {
			this.slope = slope;
		}

		@Override
		public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
			probeStarts.add(pStart);
			double z = pStart[2];
			double cx = 0.001 + slope * (z + 0.005);
			double[][] poses = {
					pose(cx + 0.010, -0.002, z), pose(cx + 0.010, 0.002, z),
					pose(cx - 0.010, -0.002, z), pose(cx - 0.010, 0.002, z),
					pose(cx - 0.002, 0.010, z), pose(cx + 0.002, 0.010, z),
					pose(cx - 0.002, -0.010, z), pose(cx + 0.002, -0.010, z)
			};
			return new CircleData(4, 4, poses);
		}

		@Override
		public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
			return ZSearchResult.ok(new double[] { 0.001, 0, -0.005, 0, 0, 0 });
		}

		private static double[] pose(double x, double y, double z) {
			return new double[] { x, y, z, 0, 0, 0 };
		}
	}

	private static TCPCalibrationSpec spec() {
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pStart = new double[] { 0, 0, -0.005, 0, 0, 0 };
		s.pRef = new double[] { 0, 0, -0.003, 0, 0, 0 };
		s.refTcp = new double[6];
		s.adjustAngle = true; // dz = 5 mm, max angle 10 deg (defaults)
		return s;
	}

	@Test
	void straightToolWithXyzDriftMeasuresNoTilt() {
		DriftedToolTransport t = new DriftedToolTransport(0);
		TCPCalibrationResult r = new TCPCalibrationRunner(t).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.OK, r.status,
				"1 mm drift on a straight tool must not read as an >10 deg tilt");
		assertEquals(0, r.correction[3], 1e-9);
		assertEquals(0, r.correction[4], 1e-9);
	}

	@Test
	void orientationCircleAnchorsOnTheMeasuredPose() {
		DriftedToolTransport t = new DriftedToolTransport(0);
		new TCPCalibrationRunner(t).calibrate(spec());
		assertEquals(2, t.probeStarts.size());
		// Raised circle: measured pose (0.001, 0, -0.005) + 5 mm base Z, NOT pRef + 5 mm.
		assertArrayEquals(new double[] { 0.001, 0, 0, 0, 0, 0 }, t.probeStarts.get(1), 1e-12);
	}

	@Test
	void tiltedToolStillMeasuresItsTilt() {
		double slope = 0.05; // tan(tilt) ~ 2.86 deg
		DriftedToolTransport t = new DriftedToolTransport(slope);
		TCPCalibrationResult r = new TCPCalibrationRunner(t).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.OK, r.status);
		assertEquals(0, r.correction[3], 1e-9);                 // RX: no Y component
		assertEquals(Math.atan(slope), r.correction[4], 1e-6);  // RY from the X displacement
	}
}
