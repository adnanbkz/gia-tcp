package com.GIA.GIATcp.tcpcalibration.probe;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;

/** Wire-protocol parsing/formatting for the realtime probe replies, shared by both transports. */
final class CalibCsv {

	private CalibCsv() {
	}

	/** Parses "x,y,z,rx,ry,rz" into a 6-element SI pose; throws on malformed input. */
	static double[] parsePoseCsv(String csv) {
		String[] f = csv.trim().split(",");
		if (f.length != 6) {
			throw new NumberFormatException("expected 6 fields: " + csv);
		}
		double[] p = new double[6];
		for (int i = 0; i < 6; i++) {
			p[i] = Double.parseDouble(f[i].trim());
		}
		return p;
	}

	/** Parses a "{tag};x,y,z,rx,ry,rz" reply, or null on FAIL/malformed. */
	static double[] parseTaggedPose(String line, String tag) {
		if (line == null) {
			return null;
		}
		String[] t = line.trim().split(";");
		if (t.length < 2 || !tag.equals(t[0]) || "FAIL".equals(t[1].trim())) {
			return null;
		}
		try {
			return parsePoseCsv(t[1]);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Parses "C;count1;count2;&lt;8 poses&gt;" (each "x,y,z,rx,ry,rz") into a {@link CircleData}, or null. */
	static CircleData parseCircle(String line) {
		if (line == null) {
			return null;
		}
		String[] t = line.trim().split(";");
		if (t.length < 11 || !"C".equals(t[0])) {
			return null;
		}
		try {
			int c1 = Integer.parseInt(t[1].trim());
			int c2 = Integer.parseInt(t[2].trim());
			double[][] poses = new double[8][];
			for (int i = 0; i < 8; i++) {
				poses[i] = parsePoseCsv(t[3 + i]);
			}
			return new CircleData(c1, c2, poses);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Formats a 6-element SI pose as "x,y,z,rx,ry,rz" (the on-the-wire form, not a p[] literal). */
	static String poseCsv(double[] p) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(String.format(java.util.Locale.US, "%.6f", p[i]));
		}
		return sb.toString();
	}
}
