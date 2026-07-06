package com.GIA.GIATcp.tcpcalibration.probe;

import com.GIA.GIATcp.tcpcalibration.engine.ProbeTransport;
import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Runtime transport: the welding program (the generated URScript) drives the {@code moveC}
 * motion while this server side, running inside the {@link CalibrationServer}, tells it what
 * to probe and finally hands back the corrected TCP. One instance per calibration session
 * (one robot connection).
 *
 * <p>Wire format:
 * <ul>
 *   <li>server &rarr; robot: a single parenthesised ascii-float list
 *       {@code (op,x,y,z,rx,ry,rz,status,diam)} the robot reads with
 *       {@code socket_read_ascii_float(9, ...)}. {@code op}: 1 = probe circle, 2 = search Z,
 *       0 = done (then the pose carries the corrected TCP, {@code status} the result ordinal).</li>
 *   <li>robot &rarr; server: a text line {@code C;count1;count2;<8 poses>} after a circle, or
 *       {@code Z;<pose>} / {@code Z;FAIL} after a Z search.</li>
 * </ul>
 */
final class ServerProbeTransport implements ProbeTransport {

	static final int OP_CIRCLE = 1;
	static final int OP_SEARCH_Z = 2;
	static final int OP_DONE = 0;

	private final BufferedReader in;
	private final OutputStream out;

	ServerProbeTransport(BufferedReader in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	@Override
	public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
		try {
			sendOp(OP_CIRCLE, pStart, 0, 0.0);
			return CalibCsv.parseCircle(in.readLine());
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
		try {
			sendOp(OP_SEARCH_Z, pCentre, 0, 0.0);
			return CalibCsv.parseZ(in.readLine());
		} catch (IOException e) {
			return null;
		}
	}

	/** Terminal message: tells the robot the run is finished and hands back the corrected TCP. */
	void sendDone(TCPCalibrationResult r) throws IOException {
		double[] tcp = r.correctedTcp != null ? r.correctedTcp : new double[6];
		sendOp(OP_DONE, tcp, r.status.ordinal(), r.diameterMm);
	}

	private void sendOp(int op, double[] pose, int status, double diam) throws IOException {
		String msg = "(" + op + "," + CalibCsv.poseCsv(pose) + "," + status + ","
				+ String.format(Locale.US, "%.6f", diam) + ")\n";
		out.write(msg.getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}
}
