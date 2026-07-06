package com.GIA.GIATcp.installation.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalibParamsTest {

	@Test
	void signedZOffsetsFollowInvertZ() {
		CalibParams p = new CalibParams();
		p.searchZMm = 12.0; // explicit: the sign logic is under test, not the default value

		p.invertZ = false;
		assertEquals(12.0, p.signedSearchZMm(), 1e-9);
		assertEquals(-50.0, p.signedApproachZMm(50.0), 1e-9);
		assertEquals(5.0, p.signedImmerseZMm(5.0), 1e-9);
		assertEquals(-50.0, p.signedApproachZMm(-50.0), 1e-9);
		assertEquals(5.0, p.signedImmerseZMm(-5.0), 1e-9);

		p.invertZ = true;
		assertEquals(-12.0, p.signedSearchZMm(), 1e-9);
		assertEquals(50.0, p.signedApproachZMm(50.0), 1e-9);
		assertEquals(-5.0, p.signedImmerseZMm(5.0), 1e-9);
		assertEquals(50.0, p.signedApproachZMm(-50.0), 1e-9);
		assertEquals(-5.0, p.signedImmerseZMm(-5.0), 1e-9);
	}
}
