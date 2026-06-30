package com.GIA.GIATcp.installation.measure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aggregated result of a repeatability test: the per-run measured TCP correction
 * (X/Y/Z, mm) and diameter (mm), plus mean, sample standard deviation (sigma) and
 * peak-to-peak range over the successful runs.
 *
 * <p>This measures <b>precision</b> (run-to-run spread), not accuracy. A small sigma
 * means the sensor + motion + edge capture are repeatable; it does not by itself prove
 * the centre is correct (that needs an independent reference).</p>
 */
public final class RepeatabilityResult {

	/** true if the robot connected back and sent at least one line. */
	public boolean received;
	public int requestedRuns;
	public int successfulRuns;

	/** Raw successful runs, each [xMm, yMm, zMm, diamMm]. */
	public final List<double[]> runs = new ArrayList<double[]>();

	/** [xMm, yMm, zMm, diamMm] over successful runs. */
	public final double[] mean = new double[4];
	public final double[] sigma = new double[4];
	public final double[] range = new double[4];

	/** Combined 3D positional spread sqrt(sigmaX^2+sigmaY^2+sigmaZ^2), mm. */
	public double sigma3d;

	public static RepeatabilityResult notReceived(int requestedRuns) {
		RepeatabilityResult r = new RepeatabilityResult();
		r.received = false;
		r.requestedRuns = requestedRuns;
		return r;
	}

	/** Builds a result from the per-run status + measurement values. */
	public static RepeatabilityResult of(int requestedRuns, List<double[]> successful) {
		RepeatabilityResult r = new RepeatabilityResult();
		r.received = true;
		r.requestedRuns = requestedRuns;
		r.runs.addAll(successful);
		r.successfulRuns = successful.size();
		if (r.successfulRuns == 0) {
			return r;
		}
		for (int axis = 0; axis < 4; axis++) {
			double sum = 0;
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (double[] run : successful) {
				sum += run[axis];
				min = Math.min(min, run[axis]);
				max = Math.max(max, run[axis]);
			}
			double mean = sum / r.successfulRuns;
			r.mean[axis] = mean;
			r.range[axis] = max - min;
			if (r.successfulRuns > 1) {
				double sq = 0;
				for (double[] run : successful) {
					double d = run[axis] - mean;
					sq += d * d;
				}
				r.sigma[axis] = Math.sqrt(sq / (r.successfulRuns - 1));
			}
		}
		r.sigma3d = Math.sqrt(r.sigma[0] * r.sigma[0] + r.sigma[1] * r.sigma[1] + r.sigma[2] * r.sigma[2]);
		return r;
	}

	/** Human-readable multi-line report for the diagnostics log. */
	public String report() {
		StringBuilder sb = new StringBuilder();
		sb.append("---- Repeatability test ----\n");
		if (!received) {
			sb.append("No response from robot (timeout). Is a program running / is the robot reachable?\n");
			return sb.toString();
		}
		sb.append(String.format(Locale.US, "Successful runs: %d / %d%n", successfulRuns, requestedRuns));
		if (successfulRuns == 0) {
			sb.append("No successful measurement. In the simulator the light barriers do not trigger,\n");
			sb.append("so 0 successful runs is expected without the real sensor.\n");
			return sb.toString();
		}
		sb.append(String.format(Locale.US, "%-8s %10s %10s %10s%n", "axis", "mean", "sigma", "range"));
		String[] labels = { "X (mm)", "Y (mm)", "Z (mm)", "Diam(mm)" };
		for (int axis = 0; axis < 4; axis++) {
			sb.append(String.format(Locale.US, "%-8s %10.3f %10.4f %10.4f%n",
					labels[axis], mean[axis], sigma[axis], range[axis]));
		}
		sb.append(String.format(Locale.US, "3D position spread (1-sigma): %.4f mm%n", sigma3d));
		return sb.toString();
	}
}
