package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Diameter band on the runner (CAPTRON parity): the corrected diameter (raw + offset)
 * must sit within +/- diamTolMm of diamNominalMm; 0 disables the check.
 */
class TCPCalibrationRunnerDiameterTest {

	/**
	 * Synthetic geometry around an identity reference pose: beam 1 along X (crossings at
	 * x = +/-10 mm), beam 2 along Y, each crossing occluded over a 4 mm chord. Intersection
	 * lands exactly on pRef, so the XYZ correction is tiny and only the diameter varies.
	 */
	private static final class StubTransport implements ProbeTransport {

		@Override
		public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
			double[][] poses = {
					pose(0.010, -0.002), pose(0.010, 0.002),   // beam 1, crossing 1
					pose(-0.010, -0.002), pose(-0.010, 0.002), // beam 1, crossing 2
					pose(-0.002, 0.010), pose(0.002, 0.010),   // beam 2, crossing 1
					pose(-0.002, -0.010), pose(0.002, -0.010)  // beam 2, crossing 2
			};
			return new CircleData(4, 4, poses);
		}

		@Override
		public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
			return ZSearchResult.ok(new double[] { 0, 0, -0.005, 0, 0, 0 });
		}

		private static double[] pose(double x, double y) {
			return new double[] { x, y, 0, 0, 0, 0 };
		}
	}

	private static TCPCalibrationSpec spec() {
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pRef = new double[6];
		s.refTcp = new double[6];
		return s;
	}

	private static TCPCalibrationResult run(TCPCalibrationSpec s) {
		return new TCPCalibrationRunner(new StubTransport()).calibrate(s);
	}

	@Test
	void diameterCheckDisabledWhenTolOrNominalMissing() {
		TCPCalibrationResult r = run(spec());
		assertEquals(TCPCalibrationResult.Status.OK, r.status);
		assertEquals(4.0, r.diameterMm, 1e-9); // mean 4 mm chord, no offset
	}

	@Test
	void diameterWithinBandIsOk() {
		TCPCalibrationSpec s = spec();
		s.diamNominalMm = 4.0;
		s.diamTolMm = 1.0;
		assertEquals(TCPCalibrationResult.Status.OK, run(s).status);
	}

	@Test
	void diameterOutsideBandIsOutOfTolerance() {
		TCPCalibrationSpec s = spec();
		s.diamNominalMm = 8.0;
		s.diamTolMm = 1.0;
		assertEquals(TCPCalibrationResult.Status.OUT_OF_TOLERANCE, run(s).status);
	}

	@Test
	void offsetIsAppliedBeforeTheBand() {
		TCPCalibrationSpec s = spec();
		s.diamOffsetMm = 4.0; // real - referenced, as the runtime node computes it
		s.diamNominalMm = 8.0;
		s.diamTolMm = 1.0;
		TCPCalibrationResult r = run(s);
		assertEquals(TCPCalibrationResult.Status.OK, r.status);
		assertEquals(8.0, r.diameterMm, 1e-9);
	}
}
