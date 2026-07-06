package com.GIA.GIATcp.tcpcalibration.probe;

import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Z-search reply parsing: pose on success, typed failure reasons, legacy FAIL fallback. */
class CalibCsvZParseTest {

	@Test
	void parsesPose() {
		ZSearchResult z = CalibCsv.parseZ("Z;0.1,0.2,-0.3,0,0,1.5");
		assertNull(z.failure);
		assertArrayEquals(new double[] { 0.1, 0.2, -0.3, 0, 0, 1.5 }, z.pose, 1e-12);
	}

	@Test
	void mapsFailureReasons() {
		assertEquals(TCPCalibrationResult.Status.INPUT_LOW_AT_CENTER, CalibCsv.parseZ("Z;FAIL;LOW").failure);
		assertEquals(TCPCalibrationResult.Status.SEARCH_Z_FAILED, CalibCsv.parseZ("Z;FAIL;SEARCH").failure);
		assertEquals(TCPCalibrationResult.Status.IMMERSE_FAILED, CalibCsv.parseZ("Z;FAIL;IMMERSE").failure);
	}

	@Test
	void legacyFailWithoutReasonMapsToSearchZFailed() {
		ZSearchResult z = CalibCsv.parseZ("Z;FAIL");
		assertNull(z.pose);
		assertEquals(TCPCalibrationResult.Status.SEARCH_Z_FAILED, z.failure);
	}

	@Test
	void missingOrMalformedLineIsNull() {
		assertNull(CalibCsv.parseZ(null));
		assertNull(CalibCsv.parseZ("C;4;4"));
		assertNull(CalibCsv.parseZ("Z;not,a,pose"));
	}
}
