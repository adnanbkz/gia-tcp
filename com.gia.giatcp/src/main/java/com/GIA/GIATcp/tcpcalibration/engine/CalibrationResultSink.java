package com.GIA.GIATcp.tcpcalibration.engine;

/**
 * Receives a calibration result so it can be persisted into the installation store.
 * Implemented by the installation node and registered with {@link CalibrationServer},
 * so a calibration run by a <b>program</b> (Play, Local mode) can write its baseline
 * correction back to the installation — the "referencing" path that needs no Remote
 * Control / script injection.
 */
public interface CalibrationResultSink {

	/**
	 * Stores a successful calibration for a TCP slot. Called from a background (server)
	 * thread; the implementation must marshal any UI/DataModel access to the EDT.
	 *
	 * @param tcpId        1-based TCP slot id
	 * @param correctionSi correction pose [x,y,z,rx,ry,rz] in SI (m, rad)
	 * @param diameterMm   measured tool diameter (mm)
	 */
	void storeCalibration(int tcpId, double[] correctionSi, double diameterMm);
}
