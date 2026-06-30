package com.GIA.GIATcp.util;

import java.util.Locale;

/** Small helpers for emitting URScript literals. */
public final class UrScript {

	private UrScript() {
	}

	/** Formats a 6-element SI pose array as a URScript p[...] literal. */
	public static String pose(double[] p) {
		if (p == null || p.length != 6) {
			return "p[0,0,0,0,0,0]";
		}
		return String.format(Locale.US, "p[%.6f,%.6f,%.6f,%.6f,%.6f,%.6f]",
				p[0], p[1], p[2], p[3], p[4], p[5]);
	}

	public static String num(double v) {
		return String.format(Locale.US, "%.6f", v);
	}

	/** Formats a 6-element SI pose as bare CSV "x,y,z,rx,ry,rz" (no p[] wrapper). */
	public static String poseCsv(double[] p) {
		if (p == null || p.length != 6) {
			return "0,0,0,0,0,0";
		}
		return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
				p[0], p[1], p[2], p[3], p[4], p[5]);
	}

	/** Converts an input code to the script-side number used by gia__getInput. */
	public static int inputCode(int code) {
		return code;
	}
}
