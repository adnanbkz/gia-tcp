package com.GIA.GIATcp.tcpcalibration.math;

/**
 * Pure-Java TCP calibration maths. NO dependency on the Universal Robots API, so the
 * same class can drive any robot (e.g. Estun): the robot only provides the realtime
 * motion + edge-pose capture; all geometry lives here.
 *
 * <p>Poses use the UR convention: a {@code double[6] = {x,y,z, rx,ry,rz}} where the
 * position is in metres and {@code (rx,ry,rz)} is an axis-angle rotation vector (radians,
 * magnitude = angle). The pose maths ({@link #poseTrans}, {@link #poseInv}) are the
 * homogeneous-transform equivalents of URScript's {@code pose_trans}/{@code pose_inv},
 * and the algorithm methods are 1:1 ports of the verified probing scripts.</p>
 *
 * <p>All inputs/outputs are in SI (metres, radians). Convert mm at the call boundary.</p>
 */
public final class TCPCalibrationMaths {

	private static final double EPS = 1e-12;

	private TCPCalibrationMaths() {
	}

	// ==================================================================
	// Pose algebra (homogeneous transforms) — equivalents of pose_trans/pose_inv
	// ==================================================================

	/** Compose two poses: result = a * b (URScript {@code pose_trans(a, b)}). */
	public static double[] poseTrans(double[] a, double[] b) {
		return homToPose(matMul(poseToHom(a), poseToHom(b)));
	}

	/** Inverse of a pose (URScript {@code pose_inv(a)}). */
	public static double[] poseInv(double[] a) {
		return homToPose(homInverse(poseToHom(a)));
	}

	/** Translational distance between two poses (positions only). */
	public static double pointDist(double[] p1, double[] p2) {
		double dx = p1[0] - p2[0];
		double dy = p1[1] - p2[1];
		double dz = p1[2] - p2[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Express base-frame pose {@code p} in {@code frame} (URScript pBase2Frame). */
	public static double[] baseToFrame(double[] p, double[] frame) {
		return poseTrans(poseInv(frame), p);
	}

	/** Express frame-relative pose {@code p} back in the base (URScript pFrame2Base). */
	public static double[] frameToBase(double[] p, double[] frame) {
		return poseTrans(frame, p);
	}

	/** Positional midpoint of two poses, keeping p1's orientation (URScript pCenter). */
	public static double[] midpoint(double[] p1, double[] p2) {
		return new double[] {
				(p1[0] + p2[0]) * 0.5, (p1[1] + p2[1]) * 0.5, (p1[2] + p2[2]) * 0.5,
				p1[3], p1[4], p1[5]
		};
	}

	// ==================================================================
	// 2D line intersection (port of gia__calc2DIntersect)
	// ==================================================================

	/**
	 * Intersection of line(pA,pB) with line(pC,pD) using the XY components of each pose.
	 * Returns {x,y} or {@code null} when the lines are (near) parallel.
	 */
	public static double[] intersect2D(double[] pA, double[] pB, double[] pC, double[] pD) {
		double ax = pA[0], ay = pA[1];
		double dax = pB[0] - pA[0], day = pB[1] - pA[1];
		double cx = pC[0], cy = pC[1];
		double dbx = pD[0] - pC[0], dby = pD[1] - pC[1];
		double denom = dax * dby - day * dbx;
		if (Math.abs(denom) < 1e-9) {
			return null;
		}
		double t = ((cx - ax) * dby - (cy - ay) * dbx) / denom;
		return new double[] { ax + t * dax, ay + t * day };
	}

	// ==================================================================
	// Probe-circle result → sensor centre (port of gia__findIntersect)
	// ==================================================================

	/** Error codes returned in {@link CenterResult#error}. */
	public static final int OK = 0;
	public static final int ERR_WRONG_POINT_COUNT = -1;
	public static final int ERR_NO_INTERSECT = -2;
	public static final int ERR_INTERSECT_TOO_FAR = -3;

	/** Result of {@link #computeCenter}: the intersection pose, measured diameter and an error code. */
	public static final class CenterResult {
		public final double[] intersect; // base-frame pose of the beam-cross centre (null on error)
		public final double diameterM;   // mean probed diameter (m)
		public final int error;          // OK or ERR_*

		CenterResult(double[] intersect, double diameterM, int error) {
			this.intersect = intersect;
			this.diameterM = diameterM;
			this.error = error;
		}
	}

	/**
	 * From the 8 edge poses captured during a probe circle (4 per beam, base frame) and the
	 * probe start pose, compute the XY beam-cross centre in the start-frame plane and the mean
	 * diameter. Equivalent of {@code gia__findIntersect}.
	 *
	 * @param edgePoses 8 base-frame poses: [beam1 ×4, beam2 ×4]
	 * @param pStart    probe start/centre pose (base frame)
	 * @param radiusM   probe radius (m), used for the sanity guard
	 */
	public static CenterResult computeCenter(double[][] edgePoses, double[] pStart, double radiusM) {
		if (edgePoses == null || edgePoses.length != 8) {
			return new CenterResult(null, 0, ERR_WRONG_POINT_COUNT);
		}
		double diam = meanDiameter(edgePoses);

		// Beam 1 line through its two crossing midpoints; beam 2 likewise.
		double[] pA = baseToFrame(midpoint(edgePoses[0], edgePoses[1]), pStart);
		double[] pB = baseToFrame(midpoint(edgePoses[2], edgePoses[3]), pStart);
		double[] pC = baseToFrame(midpoint(edgePoses[4], edgePoses[5]), pStart);
		double[] pD = baseToFrame(midpoint(edgePoses[6], edgePoses[7]), pStart);

		double[] xy = intersect2D(pA, pB, pC, pD);
		if (xy == null) {
			return new CenterResult(null, diam, ERR_NO_INTERSECT);
		}

		// Back to base, keeping the start-plane Z and the start orientation.
		double[] pBase = frameToBase(new double[] { xy[0], xy[1], 0, 0, 0, 0 }, pStart);
		double[] pIntersect = new double[] {
				pBase[0], pBase[1], pBase[2], pStart[3], pStart[4], pStart[5]
		};
		if (pointDist(pStart, pIntersect) > radiusM) {
			return new CenterResult(null, diam, ERR_INTERSECT_TOO_FAR);
		}
		return new CenterResult(pIntersect, diam, OK);
	}

	/** Mean of the chord lengths the tool occluded at each crossing (port of gia__meanDiameter). */
	public static double meanDiameter(double[][] edgePoses) {
		double sum = 0;
		int count = 0;
		for (int i = 0; i + 1 < edgePoses.length; i += 2) {
			sum += pointDist(edgePoses[i], edgePoses[i + 1]);
			count++;
		}
		return count > 0 ? sum / count : 0;
	}

	// ==================================================================
	// TCP correction (port of inst.script lines 63-64) — verified
	// ==================================================================

	/**
	 * Correction such that {@code correctedTcp = poseTrans(refTcp, correction)} places the tool
	 * at {@code pRef} when the flange is where it was at measurement.
	 * correction = pSearchZ⁻¹ · pRef.
	 *
	 * @param pRef     taught reference centre pose (base frame)
	 * @param pSearchZ measured pose (base frame) where the tip sat at the true sensor centre
	 */
	public static double[] correction(double[] pRef, double[] pSearchZ) {
		return poseInv(poseTrans(poseInv(pRef), pSearchZ));
	}

	/** Apply a correction to the reference TCP offset → corrected TCP offset. */
	public static double[] correctedTcp(double[] refTcp, double[] correction) {
		return poseTrans(refTcp, correction);
	}

	// ==================================================================
	// Angle RX/RY (port of gia__calcAngleXY + gia__correctAngle)
	// ==================================================================

	/**
	 * Tool tilt [RX, RY] (radians) from the intersection measured at a height offset,
	 * expressed in the reference frame. Iterated by the caller until it converges.
	 */
	public static double[] calcAngleXY(double[] pRef, double[] pIntersect) {
		double[] p = baseToFrame(pIntersect, pRef);
		double ax = correctAngle(Math.atan2(p[0], p[2]));
		double ay = correctAngle(Math.atan2(p[1], p[2]));
		return new double[] { -ay, ax }; // [RX, RY]; RX negated by convention
	}

	/** Wrap an angle into (-90, 90] degrees (port of gia__correctAngle). */
	public static double correctAngle(double a) {
		double r90 = Math.toRadians(90);
		double r180 = Math.toRadians(180);
		if (a > r90) {
			return a - r180;
		} else if (a < -r90) {
			return a + r180;
		}
		return a;
	}

	// ==================================================================
	// Tolerances
	// ==================================================================

	/** True when each of X,Y,Z of {@code correction} (m) is within +/- the given tolerance (m). */
	public static boolean withinTol(double[] correction, double[] tolXYZ) {
		for (int i = 0; i < 3; i++) {
			if (Math.abs(correction[i]) > tolXYZ[i]) {
				return false;
			}
		}
		return true;
	}

	/** True when |measured - nominal| <= tol (all in the same unit). */
	public static boolean withinTolVal(double measured, double nominal, double tol) {
		return Math.abs(measured - nominal) <= tol;
	}

	// ==================================================================
	// Low-level homogeneous-transform helpers (4x4)
	// ==================================================================

	private static double[][] poseToHom(double[] p) {
		double[][] r = rotVecToMatrix(p[3], p[4], p[5]);
		return new double[][] {
				{ r[0][0], r[0][1], r[0][2], p[0] },
				{ r[1][0], r[1][1], r[1][2], p[1] },
				{ r[2][0], r[2][1], r[2][2], p[2] },
				{ 0, 0, 0, 1 }
		};
	}

	private static double[] homToPose(double[][] t) {
		double[] rv = matrixToRotVec(t);
		return new double[] { t[0][3], t[1][3], t[2][3], rv[0], rv[1], rv[2] };
	}

	private static double[][] matMul(double[][] a, double[][] b) {
		double[][] m = new double[4][4];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double s = 0;
				for (int k = 0; k < 4; k++) {
					s += a[i][k] * b[k][j];
				}
				m[i][j] = s;
			}
		}
		return m;
	}

	/** Inverse of a rigid transform: [R^T | -R^T t]. */
	private static double[][] homInverse(double[][] t) {
		double[][] m = new double[4][4];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				m[i][j] = t[j][i]; // R^T
			}
		}
		for (int i = 0; i < 3; i++) {
			m[i][3] = -(m[i][0] * t[0][3] + m[i][1] * t[1][3] + m[i][2] * t[2][3]);
		}
		m[3][3] = 1;
		return m;
	}

	/** Axis-angle rotation vector → 3x3 rotation matrix (Rodrigues). */
	private static double[][] rotVecToMatrix(double rx, double ry, double rz) {
		double theta = Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (theta < EPS) {
			return new double[][] { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
		}
		double kx = rx / theta, ky = ry / theta, kz = rz / theta;
		double c = Math.cos(theta), s = Math.sin(theta), v = 1 - c;
		return new double[][] {
				{ c + kx * kx * v, kx * ky * v - kz * s, kx * kz * v + ky * s },
				{ ky * kx * v + kz * s, c + ky * ky * v, ky * kz * v - kx * s },
				{ kz * kx * v - ky * s, kz * ky * v + kx * s, c + kz * kz * v }
		};
	}

	/** 3x3 rotation matrix → axis-angle rotation vector (inverse Rodrigues, π-safe). */
	private static double[] matrixToRotVec(double[][] m) {
		double trace = m[0][0] + m[1][1] + m[2][2];
		double cos = (trace - 1) * 0.5;
		if (cos > 1) {
			cos = 1;
		} else if (cos < -1) {
			cos = -1;
		}
		double theta = Math.acos(cos);
		if (theta < EPS) {
			return new double[] { 0, 0, 0 };
		}
		double sin = Math.sin(theta);
		if (Math.abs(sin) > 1e-6) {
			double f = theta / (2 * sin);
			return new double[] {
					(m[2][1] - m[1][2]) * f,
					(m[0][2] - m[2][0]) * f,
					(m[1][0] - m[0][1]) * f
			};
		}
		// theta ~ pi: axis from the largest diagonal of (R + I)/2 = k k^T.
		double xx = (m[0][0] + 1) * 0.5, yy = (m[1][1] + 1) * 0.5, zz = (m[2][2] + 1) * 0.5;
		double kx, ky, kz;
		if (xx >= yy && xx >= zz) {
			kx = Math.sqrt(Math.max(xx, 0));
			ky = (m[0][1] + m[1][0]) / (4 * kx);
			kz = (m[0][2] + m[2][0]) / (4 * kx);
		} else if (yy >= zz) {
			ky = Math.sqrt(Math.max(yy, 0));
			kx = (m[0][1] + m[1][0]) / (4 * ky);
			kz = (m[1][2] + m[2][1]) / (4 * ky);
		} else {
			kz = Math.sqrt(Math.max(zz, 0));
			kx = (m[0][2] + m[2][0]) / (4 * kz);
			ky = (m[1][2] + m[2][1]) / (4 * kz);
		}
		double n = Math.sqrt(kx * kx + ky * ky + kz * kz);
		if (n < EPS) {
			return new double[] { 0, 0, 0 };
		}
		return new double[] { theta * kx / n, theta * ky / n, theta * kz / n };
	}
}
