package com.GIA.GIATcp.util.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Minimal client for the UR Dashboard Server (port 29999). Used to detect, before
 * sending a calibration program, whether the e-Series is in <b>Local</b> control mode —
 * in which case externally injected URScript (primary/secondary interface) is silently
 * ignored and the calibration would just time out. Blocking I/O; call off the EDT.
 */
public final class DashboardClient {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final int PORT = 29999;
	private static final int TIMEOUT_MS = 1500;

	private final String host;

	public DashboardClient() {
		this("127.0.0.1");
	}

	public DashboardClient(String host) {
		this.host = host;
	}

	/**
	 * Queries {@code is in remote control}.
	 *
	 * @return {@code TRUE}/{@code FALSE} when the controller answers; {@code null} when the
	 *         answer is unknown — server unreachable or the command is unsupported (PolyScope
	 *         &lt; 5.6). Callers must treat {@code null} as "do not block".
	 */
	public Boolean isInRemoteControl() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, PORT), TIMEOUT_MS);
			socket.setSoTimeout(TIMEOUT_MS);
			BufferedReader in = new BufferedReader(
					new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
			in.readLine(); // banner: "Connected: Universal Robots Dashboard Server"
			OutputStream out = socket.getOutputStream();
			out.write("is in remote control\n".getBytes(StandardCharsets.US_ASCII));
			out.flush();
			String reply = in.readLine();
			if (reply == null) {
				return null;
			}
			reply = reply.trim().toLowerCase(Locale.ROOT);
			if (reply.contains("true")) {
				return Boolean.TRUE;
			}
			if (reply.contains("false")) {
				return Boolean.FALSE;
			}
			logger.debug("Dashboard 'is in remote control' unrecognised reply: {}", reply);
			return null;
		} catch (IOException e) {
			logger.debug("Dashboard query failed on {}:{}", host, PORT, e);
			return null;
		}
	}
}
