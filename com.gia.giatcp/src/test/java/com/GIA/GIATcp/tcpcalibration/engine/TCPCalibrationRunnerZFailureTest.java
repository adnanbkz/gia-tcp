package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The runner surfaces the robot-reported Z failure reason instead of a generic status. */
class TCPCalibrationRunnerZFailureTest {

	private static final class FailingZTransport implements ProbeTransport {

		private final ZSearchResult z;

		FailingZTransport(ZSearchResult z) {
			this.z = z;
		}

		@Override
		public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
			double[][] poses = {
					pose(0.010, -0.002), pose(0.010, 0.002),
					pose(-0.010, -0.002), pose(-0.010, 0.002),
					pose(-0.002, 0.010), pose(0.002, 0.010),
					pose(-0.002, -0.010), pose(0.002, -0.010)
			};
			return new CircleData(4, 4, poses);
		}

		@Override
		public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
			return z;
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

	@Test
	void inputLowReasonIsSurfaced() {
		TCPCalibrationResult r = new TCPCalibrationRunner(new FailingZTransport(
				ZSearchResult.fail(TCPCalibrationResult.Status.INPUT_LOW_AT_CENTER))).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.INPUT_LOW_AT_CENTER, r.status);
	}

	@Test
	void immerseReasonIsSurfaced() {
		TCPCalibrationResult r = new TCPCalibrationRunner(new FailingZTransport(
				ZSearchResult.fail(TCPCalibrationResult.Status.IMMERSE_FAILED))).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.IMMERSE_FAILED, r.status);
	}

	@Test
	void missingReplyKeepsLegacySearchZFailed() {
		TCPCalibrationResult r = new TCPCalibrationRunner(new FailingZTransport(null)).calibrate(spec());
		assertEquals(TCPCalibrationResult.Status.SEARCH_Z_FAILED, r.status);
	}
}
