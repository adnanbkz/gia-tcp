package com.GIA.GIATcp.installation.model;

/**
 * A selectable digital input for the light-barrier cables. The {@code code} is
 * the script-side number used by gia__getInput: 0..7 standard digital inputs,
 * 10..17 configurable inputs.
 */
public class IoOption {

	public final int code;
	public final String label;

	public IoOption(int code, String label) {
		this.code = code;
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}
