package com.GIA.GIATcp.tcpcalibration.probe;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the INIT handshake contract, including the optional tcpId/persist tail. */
class CalibrationServerParseInitTest {

	private static final String POSES = "0.1,0.2,0.3,0,0,0;0.01,0.02,0.03,0,0,0";

	@Test
	void legacyLineParsesWithoutPersist() {
		// 12 tokens: INIT;pRef;refTcp;radius;tolX;tolY;tolZ;adj;offZ;maxRx;maxRy;diamOff
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertTrue(s != null);
		assertEquals(15.0, s.radiusMm, 1e-9);
		assertEquals(0.001, s.tolXYZm[0], 1e-9); // 1 mm -> m
		assertFalse(s.adjustAngle);
		assertEquals(0, s.tcpId);
		assertFalse(s.persistToInstallation, "legacy line must default persist to false");
	}

	@Test
	void newLineParsesTcpIdAndPersist() {
		// + tcpId;persist
		String line = "INIT;" + POSES + ";15;999;999;999;0;5;10;10;0;7;1";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertTrue(s != null);
		assertEquals(7, s.tcpId);
		assertTrue(s.persistToInstallation);
	}

	@Test
	void persistZeroIsFalse() {
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0;3;0";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertEquals(3, s.tcpId);
		assertFalse(s.persistToInstallation);
	}

	@Test
	void malformedReturnsNull() {
		assertNull(CalibrationServer.parseInit(null));
		assertNull(CalibrationServer.parseInit("NOPE;1;2;3"));
		assertNull(CalibrationServer.parseInit("INIT;too;few"));
	}
}
