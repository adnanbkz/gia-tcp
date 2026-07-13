package com.GIA.GIATcp.installation.calib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 5510 reply must only count as received with the full 8 finite fields the
 * reporting script always sends. A truncated line (e.g. a bare "0") used to parse as a
 * successful referencing with a zero correction and mark the TCP as calibrated.
 */
class CalibrationResultParseTest {

	@Test
	void fullLineParses() {
		CalibrationResult r = CalibrationResult.parse("0,1.5,-0.25,0.1,0,0,0,1.18");
		assertTrue(r.received);
		assertTrue(r.isSuccess());
		assertEquals(0.0015, r.correctionSi[0], 1e-12);
		assertEquals(-0.00025, r.correctionSi[1], 1e-12);
		assertEquals(1.18, r.diameterMm, 1e-9);
	}

	@Test
	void errorStatusStillParsesWithAllFields() {
		CalibrationResult r = CalibrationResult.parse("-11,0,0,0,0,0,0,0");
		assertTrue(r.received);
		assertFalse(r.isSuccess());
		assertEquals("REF_ERR_11", r.statusKey());
	}

	@Test
	void truncatedLineIsNotAResult() {
		assertFalse(CalibrationResult.parse("0").received, "bare status must not be a success");
		assertFalse(CalibrationResult.parse("0,1,2,3").received);
		assertFalse(CalibrationResult.parse("").received);
		assertFalse(CalibrationResult.parse(null).received);
	}

	@Test
	void nonFiniteValuesAreRejected() {
		assertFalse(CalibrationResult.parse("0,NaN,0,0,0,0,0,1.2").received);
		assertFalse(CalibrationResult.parse("0,Infinity,0,0,0,0,0,1.2").received);
	}

	@Test
	void garbageIsRejected() {
		assertFalse(CalibrationResult.parse("hello,world,a,b,c,d,e,f").received);
	}
}
