package com.GIA.GIATcp.tcpcalibration.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the calibration maths against known inputs (the verification the boss asked for). */
class TCPCalibrationMathsTest {

	private static final double EPS = 1e-9;

	// ---- pose algebra ----

	@Test
	void poseInvIsLeftAndRightInverse() {
		double[] a = { 0.3, -0.12, 0.45, 0.4, -0.7, 1.1 };
		assertIdentity(TCPCalibrationMaths.poseTrans(a, TCPCalibrationMaths.poseInv(a)));
		assertIdentity(TCPCalibrationMaths.poseTrans(TCPCalibrationMaths.poseInv(a), a));
	}

	@Test
	void poseTransComposesTranslationAndRotation() {
		// 90 deg about Z, then translate +X by 1 in the rotated frame -> +Y in base.
		double[] rotZ90 = { 0, 0, 0, 0, 0, Math.toRadians(90) };
		double[] tx = { 1, 0, 0, 0, 0, 0 };
		double[] r = TCPCalibrationMaths.poseTrans(rotZ90, tx);
		assertEquals(0, r[0], 1e-9);
		assertEquals(1, r[1], 1e-9);
		assertEquals(0, r[2], 1e-9);
	}

	// ---- 2D intersection ----

	@Test
	void intersect2DAtOrigin() {
		double[] xy = TCPCalibrationMaths.intersect2D(
				p(-0.01, 0), p(0.01, 0),   // X axis
				p(0, -0.01), p(0, 0.01));  // Y axis
		assertNotNull(xy);
		assertEquals(0, xy[0], EPS);
		assertEquals(0, xy[1], EPS);
	}

	@Test
	void intersect2DOffCentre() {
		double[] xy = TCPCalibrationMaths.intersect2D(
				p(-0.01, 0.002), p(0.01, 0.002),   // horizontal line y = 0.002
				p(0.003, -0.01), p(0.003, 0.01));  // vertical line x = 0.003
		assertNotNull(xy);
		assertEquals(0.003, xy[0], EPS);
		assertEquals(0.002, xy[1], EPS);
	}

	@Test
	void intersect2DParallelReturnsNull() {
		assertNull(TCPCalibrationMaths.intersect2D(
				p(0, 0), p(1, 0), p(0, 1), p(1, 1)));
	}

	// ---- centre from probe circle ----

	@Test
	void computeCenterFindsCrossedBeams() {
		double[] pStart = { 0, 0, 0, 0, 0, 0 };
		double[][] edges = {
				pose(-0.01, 0.002, 0), pose(-0.01, 0.002, 0), // beam1 crossing 1
				pose(0.01, 0.002, 0), pose(0.01, 0.002, 0),    // beam1 crossing 2  -> line y=0.002
				pose(0.003, -0.01, 0), pose(0.003, -0.01, 0),  // beam2 crossing 1
				pose(0.003, 0.01, 0), pose(0.003, 0.01, 0)     // beam2 crossing 2  -> line x=0.003
		};
		TCPCalibrationMaths.CenterResult r = TCPCalibrationMaths.computeCenter(edges, pStart, 0.05);
		assertEquals(TCPCalibrationMaths.OK, r.error);
		assertNotNull(r.intersect);
		assertEquals(0.003, r.intersect[0], 1e-9);
		assertEquals(0.002, r.intersect[1], 1e-9);
	}

	@Test
	void computeCenterRejectsIntersectTooFar() {
		double[] pStart = { 0, 0, 0, 0, 0, 0 };
		double[][] edges = {
				pose(0.09, 0.002, 0), pose(0.09, 0.002, 0),
				pose(0.11, 0.002, 0), pose(0.11, 0.002, 0),
				pose(0.10, -0.01, 0), pose(0.10, -0.01, 0),
				pose(0.10, 0.01, 0), pose(0.10, 0.01, 0)
		};
		TCPCalibrationMaths.CenterResult r = TCPCalibrationMaths.computeCenter(edges, pStart, 0.02);
		assertEquals(TCPCalibrationMaths.ERR_INTERSECT_TOO_FAR, r.error);
	}

	@Test
	void computeCenterRejectsWrongCount() {
		TCPCalibrationMaths.CenterResult r = TCPCalibrationMaths.computeCenter(
				new double[3][6], new double[] { 0, 0, 0, 0, 0, 0 }, 0.05);
		assertEquals(TCPCalibrationMaths.ERR_WRONG_POINT_COUNT, r.error);
	}

	// ---- TCP correction (the verified core) ----

	@Test
	void correctionIsIdentityWhenMeasuredEqualsReference() {
		double[] pRef = { 0.4, 0.1, 0.3, 0.2, -0.3, 0.5 };
		double[] corr = TCPCalibrationMaths.correction(pRef, pRef);
		assertIdentity(corr);
		double[] refTcp = { 0, 0, 0.35, 0, 0, 0 };
		double[] newTcp = TCPCalibrationMaths.correctedTcp(refTcp, corr);
		for (int i = 0; i < 6; i++) {
			assertEquals(refTcp[i], newTcp[i], 1e-9);
		}
	}

	@Test
	void correctionRecoversAKnownOffset() {
		// Tool ended 1 mm off in +X vs the taught centre -> correction is -1 mm in X.
		double[] pRef = { 0, 0, 0, 0, 0, 0 };
		double[] pSearch = { 0.001, 0, 0, 0, 0, 0 };
		double[] corr = TCPCalibrationMaths.correction(pRef, pSearch);
		assertEquals(-0.001, corr[0], 1e-12);
		assertEquals(0, corr[1], 1e-12);
		assertEquals(0, corr[2], 1e-12);
	}

	// ---- angle ----

	@Test
	void calcAngleXYDecomposesTilt() {
		double[] pRef = { 0, 0, 0, 0, 0, 0 };
		double[] pIntersect = { 0.001, 0, 0.01, 0, 0, 0 }; // x offset at height z
		double[] a = TCPCalibrationMaths.calcAngleXY(pRef, pIntersect);
		assertEquals(0, a[0], 1e-9);                          // RX
		assertEquals(Math.atan2(0.001, 0.01), a[1], 1e-9);    // RY
	}

	// ---- tolerances ----

	@Test
	void withinTolBand() {
		double[] tol = { 0.001, 0.001, 0.001 };
		assertTrue(TCPCalibrationMaths.withinTol(new double[] { 0.0005, -0.0009, 0.0001, 0, 0, 0 }, tol));
		assertTrue(!TCPCalibrationMaths.withinTol(new double[] { 0.0015, 0, 0, 0, 0, 0 }, tol));
	}

	// ---- helpers ----

	private static double[] p(double x, double y) {
		return new double[] { x, y, 0, 0, 0, 0 };
	}

	private static double[] pose(double x, double y, double z) {
		return new double[] { x, y, z, 0, 0, 0 };
	}

	private static void assertIdentity(double[] pose) {
		for (int i = 0; i < 6; i++) {
			assertEquals(0, pose[i], 1e-9, "component " + i);
		}
	}
}
