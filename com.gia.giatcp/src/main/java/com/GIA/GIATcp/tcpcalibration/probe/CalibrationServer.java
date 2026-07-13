package com.GIA.GIATcp.tcpcalibration.probe;

import com.GIA.GIATcp.tcpcalibration.engine.CalibrationResultSink;
import com.GIA.GIATcp.tcpcalibration.engine.TCPCalibrationRunner;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Persistent loopback server that lets the welding program calibrate the TCP <b>at runtime</b>.
 * The URCap's Java runs while PolyScope is up (including during program execution), so this
 * server stays alive the whole time — the same role CAPTRON's embedded XML-RPC server played.
 *
 * <p>When a running program reaches a GIA TCP node, the generated URScript opens a socket here,
 * sends the spec, and is then driven step by step ({@link ServerProbeTransport}) through the
 * {@code moveC} probing while this server does ALL the geometry in {@link TCPCalibrationMaths}
 * and hands back the corrected TCP. Started/stopped from the bundle {@code Activator}.
 */
public final class CalibrationServer {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	public static final int PORT = 5512;
	// Per-read timeout for robot replies: each probe primitive (circle / Z search) must
	// finish its motion within this. 120 s covers the slowest speed setting with margin.
	private static final int SESSION_TIMEOUT_MS = 120000;

	/**
	 * Sink the installation node registers so a program-run calibration can persist its
	 * baseline ("referencing" in Local mode). Static because the server is a single bundle-wide
	 * instance while the installation contribution is created per node; last registration wins.
	 */
	private static volatile CalibrationResultSink resultSink;

	public static void setResultSink(CalibrationResultSink sink) {
		resultSink = sink;
	}

	/**
	 * Last measured deviation per TCP slot ([xMm, yMm, zMm, diamMm]), fed by runtime
	 * sessions and the live test. Backs the node's "Previous" column (CAPTRON shows the
	 * last deviation next to the Min/Max bands so users can tune them with real data).
	 */
	private static final java.util.concurrent.ConcurrentHashMap<Integer, double[]> LAST_RESULT =
			new java.util.concurrent.ConcurrentHashMap<Integer, double[]>();

	public static void recordResult(int tcpId, TCPCalibrationResult r) {
		if (tcpId <= 0 || r == null || r.correction == null) {
			return;
		}
		LAST_RESULT.put(tcpId, new double[] {
				r.correction[0] * 1000.0, r.correction[1] * 1000.0, r.correction[2] * 1000.0, r.diameterMm });
	}

	/** [xMm, yMm, zMm, diamMm] of the last measured run for this TCP slot, or null. */
	public static double[] lastResultFor(int tcpId) {
		return LAST_RESULT.get(tcpId);
	}

	private volatile boolean running;
	private ServerSocket serverSocket;
	private Thread acceptThread;

	public synchronized void start() {
		if (running) {
			return;
		}
		try {
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress("127.0.0.1", PORT));
		} catch (IOException e) {
			logger.warn("GIA TCP runtime calibration server could not bind to {}", PORT, e);
			return;
		}
		running = true;
		acceptThread = new Thread(this::acceptLoop, "gia-tcp-calib-server");
		acceptThread.setDaemon(true);
		acceptThread.start();
		logger.info("GIA TCP runtime calibration server listening on 127.0.0.1:{}", PORT);
	}

	public synchronized void stop() {
		running = false;
		closeQuietly(serverSocket);
		if (acceptThread != null) {
			acceptThread.interrupt();
		}
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				Thread session = new Thread(() -> handleSession(socket), "gia-tcp-calib-session");
				session.setDaemon(true);
				session.start();
			} catch (IOException e) {
				if (running) {
					logger.debug("calibration server accept error", e);
				}
			}
		}
	}

	private void handleSession(Socket socket) {
		try (Socket s = socket;
				BufferedReader in = new BufferedReader(
						new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
				OutputStream out = s.getOutputStream()) {
			s.setSoTimeout(SESSION_TIMEOUT_MS);
			String init = in.readLine();
			TCPCalibrationSpec spec = parseInit(init);
			ServerProbeTransport transport = new ServerProbeTransport(in, out);
			if (spec == null) {
				logger.debug("calibration server: bad INIT line: {}", init);
				transport.sendDone(TCPCalibrationResult.error(TCPCalibrationResult.Status.NO_ROBOT_REPLY));
				return;
			}
			TCPCalibrationResult result = new TCPCalibrationRunner(transport).calibrate(spec);
			transport.sendDone(result);
			logger.debug("calibration server: session result {}", result.status);
			recordResult(spec.tcpId, result);
			maybePersist(spec, result);
		} catch (IOException e) {
			logger.debug("calibration server session error", e);
		}
	}

	/**
	 * Parses the handshake line the robot sends:
	 * {@code INIT;<pRefCsv>;<refTcpCsv>;radiusMm;tolXmm;tolYmm;tolZmm;adj;offZmm;maxRx;maxRy;diamOffMm[;tcpId;persist[;tolDmm;diamNomMm[;pStartCsv]]]}.
	 * The trailing fields are optional (older programs omit them). {@code pRef} is the
	 * correction reference (referenced pose, CAPTRON h()); {@code pStart} the taught centre
	 * the circle runs around (falls back to pRef when absent). Motion params
	 * (in1/in2, acc, vel, overrun, searchZ) are not needed here — the robot bakes them.
	 */
	static TCPCalibrationSpec parseInit(String line) {
		if (line == null) {
			return null;
		}
		String[] t = line.trim().split(";");
		if (t.length < 12 || !"INIT".equals(t[0])) {
			return null;
		}
		try {
			TCPCalibrationSpec s = new TCPCalibrationSpec();
			s.pRef = CalibCsv.parsePoseCsv(t[1]);
			s.refTcp = CalibCsv.parsePoseCsv(t[2]);
			s.radiusMm = Double.parseDouble(t[3].trim());
			s.tolXYZm = new double[] {
					Double.parseDouble(t[4].trim()) / 1000.0,
					Double.parseDouble(t[5].trim()) / 1000.0,
					Double.parseDouble(t[6].trim()) / 1000.0 };
			s.adjustAngle = Double.parseDouble(t[7].trim()) != 0.0;
			s.orientationDzMm = Double.parseDouble(t[8].trim());
			s.maxAngleRxDeg = Double.parseDouble(t[9].trim());
			s.maxAngleRyDeg = Double.parseDouble(t[10].trim());
			s.diamOffsetMm = Double.parseDouble(t[11].trim());
			if (t.length >= 14) {
				s.tcpId = (int) Math.round(Double.parseDouble(t[12].trim()));
				s.persistToInstallation = Double.parseDouble(t[13].trim()) != 0.0;
				// A persist run IS a referencing run: it re-establishes the baseline, so the
				// runner must report an identity correction (see TCPCalibrationSpec).
				s.referenceRun = s.persistToInstallation;
			}
			if (t.length >= 16) {
				s.diamTolMm = Double.parseDouble(t[14].trim());
				s.diamNominalMm = Double.parseDouble(t[15].trim());
			}
			if (t.length >= 17) {
				s.pStart = CalibCsv.parsePoseCsv(t[16]);
			}
			// Asymmetric Min/Max bands (mm on the wire): tolMinX;tolMaxX;...;tolMinD;tolMaxD.
			if (t.length >= 25) {
				s.tolMinXYZm = new double[] {
						Double.parseDouble(t[17].trim()) / 1000.0,
						Double.parseDouble(t[19].trim()) / 1000.0,
						Double.parseDouble(t[21].trim()) / 1000.0 };
				s.tolMaxXYZm = new double[] {
						Double.parseDouble(t[18].trim()) / 1000.0,
						Double.parseDouble(t[20].trim()) / 1000.0,
						Double.parseDouble(t[22].trim()) / 1000.0 };
				s.diamTolMinMm = Double.parseDouble(t[23].trim());
				s.diamTolMaxMm = Double.parseDouble(t[24].trim());
			}
			return s;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Persists a successful run to the installation store when the program asked for it
	 * (referencing). OUT_OF_TOLERANCE is still measured data, so it is stored too — the
	 * referencing flow uses wide tolerances anyway. No-op if no sink is registered.
	 */
	private static void maybePersist(TCPCalibrationSpec spec, TCPCalibrationResult result) {
		if (spec == null || !spec.persistToInstallation || spec.tcpId <= 0 || result.correction == null) {
			return;
		}
		boolean measured = result.status == TCPCalibrationResult.Status.OK
				|| result.status == TCPCalibrationResult.Status.OUT_OF_TOLERANCE;
		CalibrationResultSink sink = resultSink;
		if (measured && sink != null) {
			try {
				sink.storeCalibration(spec.tcpId, result.correction, result.diameterMm, result.measuredPose);
				logger.info("Referencing persisted for TCP {} (status {})", spec.tcpId, result.status);
			} catch (RuntimeException e) {
				logger.warn("Could not persist referencing for TCP {}", spec.tcpId, e);
			}
		}
	}

	private static void closeQuietly(ServerSocket sock) {
		if (sock != null) {
			try {
				sock.close();
			} catch (IOException ignored) {
				// best effort
			}
		}
	}
}
