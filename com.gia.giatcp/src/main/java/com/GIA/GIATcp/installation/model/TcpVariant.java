package com.GIA.GIATcp.installation.model;

/**
 * Supported physical TCP measurement-unit variants. The image base names map to
 * the sensor photos bundled under /images/tcps. UNKNOWN is used before a variant
 * is chosen in the setup wizard.
 */
public enum TcpVariant {

	UNKNOWN("<Select TCP Variant>", "unknown-tcp_220x220", null, null),
	FGL50_IK("SensoPart FGL 50-IK-50-PS-M4", "FGL50-IK", null, null);

	private final String displayName;
	private final String image;
	private final String centerImage;
	private final String doneImage;

	TcpVariant(String displayName, String image, String centerImage, String doneImage) {
		this.displayName = displayName;
		this.image = image;
		this.centerImage = centerImage;
		this.doneImage = doneImage;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getImageResource() {
		return "/images/tcps/" + image + ".png";
	}

	public String getCenterImageResource() {
		return centerImage == null ? getImageResource() : "/images/tcps/" + centerImage + ".png";
	}

	public String getDoneImageResource() {
		return doneImage == null ? getImageResource() : "/images/tcps/" + doneImage + ".png";
	}

	public boolean isSelectable() {
		return this != UNKNOWN;
	}

	public static TcpVariant fromOrdinal(int ordinal) {
		TcpVariant[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return UNKNOWN;
		}
		return values[ordinal];
	}
}
