package com.GIA.GIATcp.tcpcalibration.model;

/** Raw edge poses captured during one probe circle (4 per beam) plus the edge counts. */
public final class CircleData {

	final int count1;
	final int count2;
	public final double[][] poses; // 8 base-frame poses: [beam1 x4, beam2 x4]

	public CircleData(int count1, int count2, double[][] poses) {
		this.count1 = count1;
		this.count2 = count2;
		this.poses = poses;
	}

	public boolean valid() {
		return count1 >= 4 && count2 >= 4 && count1 % 2 == 0 && count2 % 2 == 0;
	}
}
