package com.GIA.GIATcp.tcpcalibration.probe;

import com.GIA.GIATcp.tcpcalibration.engine.ProbeTransport;
import com.GIA.GIATcp.tcpcalibration.model.CircleData;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.model.ZSearchResult;

import com.GIA.GIATcp.util.UrScript;
import com.GIA.GIATcp.util.comms.SecondaryScriptSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Live transport: Java sends the realtime library + one top-level call over the Secondary
 * interface (30002) and collects the single reply line over a loopback ServerSocket. Used by
 * the installation "test" calibration button (and any in-Java run). This is the same channel
 * CAPTRON uses; the maths stays in Java ({@link TCPCalibrationMaths}).
 */
public final class SecondaryProbeTransport implements ProbeTransport {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String SCRIPT_RESOURCE = "/scripts/tcpcalib.script";
	private static final int RETURN_PORT = 5511;
	// The robot connects back only AFTER finishing the probe motion of the primitive, so
	// accept must cover the slowest motion (slow speed circle / Z search ~ tens of seconds).
	// The reply line itself arrives immediately after the connect. Short enough that a
	// wiring/mode problem does not leave the operator staring at a frozen screen for 2 min.
	private static final int ACCEPT_TIMEOUT_MS = 60000;
	private static final int READ_TIMEOUT_MS = 15000;

	private static volatile String cachedLib;

	private final SecondaryScriptSender sender;
	private final String lib;

	public SecondaryProbeTransport() {
		this(new SecondaryScriptSender());
	}

	public SecondaryProbeTransport(SecondaryScriptSender sender) {
		this.sender = sender;
		this.lib = lib();
	}

	/** The probe library is invariant; read it from the bundle once and reuse. */
	private static String lib() {
		String l = cachedLib;
		if (l == null) {
			l = loadLib();
			cachedLib = l;
		}
		return l;
	}

	@Override
	public CircleData probeCircle(double[] pStart, TCPCalibrationSpec s) {
		String call = "tcpc__runCircle(" + UrScript.pose(pStart) + ", " + s.in1 + ", " + s.in2 + ", "
				+ UrScript.num(s.radiusMm) + ", " + UrScript.num(s.accMs2) + ", " + UrScript.num(s.velMs) + ", "
				+ UrScript.num(s.overrunDeg) + ", \"127.0.0.1\", " + RETURN_PORT + ")";
		return CalibCsv.parseCircle(sendAndReceive(setTcpLine(s) + call));
	}

	@Override
	public ZSearchResult searchZ(double[] pCentre, TCPCalibrationSpec s) {
		String call = "tcpc__runSearchZ(" + UrScript.pose(pCentre) + ", " + s.in1 + ", " + s.in2 + ", "
				+ UrScript.num(s.zSearchMm) + ", " + UrScript.num(s.zImmerseMm) + ", "
				+ UrScript.num(s.accMs2) + ", " + UrScript.num(s.velMs) + ", \"127.0.0.1\", " + RETURN_PORT + ")";
		return CalibCsv.parseZ(sendAndReceive(setTcpLine(s) + call));
	}

	/**
	 * Every injected program activates the reference TCP first (as CAPTRON does): the
	 * captured poses — and therefore the correction — are only valid measured with the
	 * reference TCP, and the controller's active TCP at click time is unknown.
	 */
	private static String setTcpLine(TCPCalibrationSpec s) {
		return s.refTcp == null ? "" : "set_tcp(" + UrScript.pose(s.refTcp) + ")\n";
	}

	/**
	 * Stops a running live probe: a normal ({@code def}) program sent over Secondary
	 * preempts the running probe program (stopping its motion), and the "STOP" line sent
	 * to the return port unblocks the pending {@link #sendAndReceive} read immediately
	 * (it parses as no valid reply, so the runner reports NO_ROBOT_REPLY).
	 */
	public static boolean sendStop(SecondaryScriptSender sender) {
		String program = "def giaTcpStop():\n"
				+ "  stopl(2.0)\n"
				+ "  if (socket_open(\"127.0.0.1\", " + RETURN_PORT + ", \"giastop\")):\n"
				+ "    socket_send_string(\"STOP\", \"giastop\")\n"
				+ "    socket_send_byte(10, \"giastop\")\n"
				+ "    socket_close(\"giastop\")\n"
				+ "  end\n"
				+ "end\n";
		return sender.send(program);
	}

	/** Sends {@code lib + call} via Secondary and returns the single reply line (or null). */
	private String sendAndReceive(String topLevelCall) {
		String program = lib + "\n" + topLevelCall + "\n";
		try (ServerSocket server = new ServerSocket()) {
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress("127.0.0.1", RETURN_PORT));
			server.setSoTimeout(ACCEPT_TIMEOUT_MS);
			if (!sender.send(program)) {
				logger.warn("Probe program could not be sent to the secondary interface (30002)");
				return null;
			}
			try (Socket socket = server.accept();
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				socket.setSoTimeout(READ_TIMEOUT_MS);
				String line = reader.readLine();
				logger.debug("calib reply: {}", line);
				return line;
			}
		} catch (IOException e) {
			logger.debug("calib socket error", e);
			return null;
		}
	}

	private static String loadLib() {
		try (InputStream in = SecondaryProbeTransport.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + SCRIPT_RESOURCE);
			}
			java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) != -1) {
				bos.write(buf, 0, n);
			}
			return new String(bos.toByteArray(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + SCRIPT_RESOURCE, e);
		}
	}
}
