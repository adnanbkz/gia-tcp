package com.GIA.GIATcp.tcpcalibration.engine;

import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;

/**
 * Abstracts how the robot is driven through the realtime probe primitives. The
 * calibration sequence and ALL the geometry ({@link TCPCalibrationMaths}) are identical
 * for both entry points — only the transport differs:
 * <ul>
 *   <li><b>live</b> ({@link SecondaryProbeTransport}): Java sends a one-shot URScript per
 *       primitive over the Secondary interface and reads the reply socket. Used by the
 *       installation "test" button.</li>
 *   <li><b>runtime</b> ({@link ServerProbeTransport}): the running welding program drives
 *       the motion and a persistent Java server replies. Used by the program node.</li>
 * </ul>
 * The robot only ever does {@code moveC} + sensor reads + {@code get_actual_tcp_pose}; the
 * maths stays in Java, so the same flow ports to other robots (e.g. Estun).
 */
public interface ProbeTransport {

	/** Moves to {@code pStart}, runs one probe circle, returns the raw edge poses (or null). */
	CircleData probeCircle(double[] pStart, TCPCalibrationSpec s);

	/** Searches Z at {@code pCentre}, returns the beam-plane pose (or null on failure). */
	double[] searchZ(double[] pCentre, TCPCalibrationSpec s);
}
