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
	void lineWithoutDiameterTailDisablesDiameterCheck() {
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0;3;0";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertEquals(0.0, s.diamTolMm, 1e-9);
		assertEquals(0.0, s.diamNominalMm, 1e-9);
	}

	@Test
	void newLineParsesDiameterToleranceAndNominal() {
		// + tolDmm;diamNomMm
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0.5;3;0;2;16.2";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertEquals(0.5, s.diamOffsetMm, 1e-9);
		assertEquals(2.0, s.diamTolMm, 1e-9);
		assertEquals(16.2, s.diamNominalMm, 1e-9);
	}

	@Test
	void lineWithoutStartPoseLeavesItNull() {
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0.5;3;0;2;16.2";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertNull(s.pStart, "no pStart tail -> circle falls back to pRef");
	}

	@Test
	void newLineParsesStartPose() {
		// + pStartCsv (taught centre; pRef then carries the referenced pose)
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0.5;3;0;2;16.2;0.4,0.5,0.6,0,0,0";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertTrue(s.pStart != null);
		assertEquals(0.4, s.pStart[0], 1e-9);
		assertEquals(0.6, s.pStart[2], 1e-9);
		assertEquals(0.1, s.pRef[0], 1e-9); // pRef untouched by the tail
	}

	@Test
	void lineWithoutMinMaxTailLeavesAsymBandsNull() {
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0.5;3;0;2;16.2;0.4,0.5,0.6,0,0,0";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertNull(s.tolMinXYZm, "no Min/Max tail -> symmetric band");
		assertNull(s.tolMaxXYZm);
	}

	@Test
	void newLineParsesAsymmetricMinMaxBands() {
		// + tolMinX;tolMaxX;tolMinY;tolMaxY;tolMinZ;tolMaxZ;tolMinD;tolMaxD (mm)
		String line = "INIT;" + POSES + ";15;1;1;1;0;5;10;10;0.5;3;0;2;16.2;0.4,0.5,0.6,0,0,0"
				+ ";-0.5;1;-0.6;1.2;-0.7;1.4;-1;3";
		TCPCalibrationSpec s = CalibrationServer.parseInit(line);
		assertEquals(-0.0005, s.tolMinXYZm[0], 1e-12);
		assertEquals(0.001, s.tolMaxXYZm[0], 1e-12);
		assertEquals(-0.0006, s.tolMinXYZm[1], 1e-12);
		assertEquals(0.0012, s.tolMaxXYZm[1], 1e-12);
		assertEquals(-0.0007, s.tolMinXYZm[2], 1e-12);
		assertEquals(0.0014, s.tolMaxXYZm[2], 1e-12);
		assertEquals(-1.0, s.diamTolMinMm, 1e-9);
		assertEquals(3.0, s.diamTolMaxMm, 1e-9);
	}

	@Test
	void malformedReturnsNull() {
		assertNull(CalibrationServer.parseInit(null));
		assertNull(CalibrationServer.parseInit("NOPE;1;2;3"));
		assertNull(CalibrationServer.parseInit("INIT;too;few"));
	}
}
