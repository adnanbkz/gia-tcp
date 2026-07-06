package com.GIA.GIATcp.tcpcalibration.model;

/**
 * Outcome of one Z search primitive: either the measured beam-plane pose, or the reason
 * it failed. The robot reports the reason as a tag ("Z;FAIL;LOW|SEARCH|IMMERSE") so the
 * final status can tell a bad teach/TCP (input low at the intersect) from a failed
 * retract (search) or a failed immerse — CAPTRON distinguishes these too (codes 4/5/31).
 */
public final class ZSearchResult {

	/** Beam-plane pose (base frame, SI); null on failure. */
	public final double[] pose;
	/** Failure reason; null when {@link #pose} is set. */
	public final TCPCalibrationResult.Status failure;

	private ZSearchResult(double[] pose, TCPCalibrationResult.Status failure) {
		this.pose = pose;
		this.failure = failure;
	}

	public static ZSearchResult ok(double[] pose) {
		return new ZSearchResult(pose, null);
	}

	public static ZSearchResult fail(TCPCalibrationResult.Status failure) {
		return new ZSearchResult(null, failure);
	}
}
