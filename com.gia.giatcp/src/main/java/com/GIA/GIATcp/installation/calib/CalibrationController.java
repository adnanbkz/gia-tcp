package com.GIA.GIATcp.installation.calib;

import com.GIA.GIATcp.installation.model.CalibParams;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.ScriptLibrary;
import com.GIA.GIATcp.util.UrScript;
import com.GIA.GIATcp.util.comms.PrimaryScriptSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Runs a live calibration / referencing on the robot from the installation page.
 * Because the motion takes physical time it is sent as a primary program (which
 * also halts any running program); the program streams the result back over a
 * loopback socket this controller listens on.
 */
public class CalibrationController {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final int ACCEPT_TIMEOUT_MS = 120000;
	private static final int READ_TIMEOUT_MS = 120000;

	private final PrimaryScriptSender sender = new PrimaryScriptSender();

	/**
	 * Sends the calibration program for the given TCP and blocks until the robot
	 * reports a result (or times out). Run off the UI thread.
	 *
	 * @param tcp          the TCP slot to calibrate
	 * @param refTcpPoseSi pose (m, rad) of the configured reference TCP
	 * @param errInterrupt whether script popups are enabled
	 * @param debugLvl     log verbosity 0..2
	 */
	public CalibrationResult calibrate(GiaTcp tcp, double[] refTcpPoseSi, boolean errInterrupt, int debugLvl) {
		String program = buildProgram(tcp, refTcpPoseSi, errInterrupt, debugLvl);
		try (ServerSocket server = new ServerSocket()) {
			server.setReuseAddress(true);
			// Loopback only: the robot connects via 127.0.0.1 and this port persists a
			// referencing — it must not be reachable from the rest of the network.
			server.bind(new java.net.InetSocketAddress(
					java.net.InetAddress.getLoopbackAddress(), Const.CALIB_RETURN_PORT));
			server.setSoTimeout(ACCEPT_TIMEOUT_MS);
			if (!sender.sendRawPrimary(program)) {
				logger.warn("Calibration program could not be sent to the primary interface (30001)");
				return CalibrationResult.timeout();
			}
			try (Socket socket = server.accept();
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				socket.setSoTimeout(READ_TIMEOUT_MS);
				String line = reader.readLine();
				logger.debug("Calibration reply: {}", line);
				return CalibrationResult.parse(line);
			}
		} catch (SocketTimeoutException e) {
			logger.warn("Calibration timed out waiting for robot reply on port {}", Const.CALIB_RETURN_PORT);
			return CalibrationResult.timeout();
		} catch (IOException e) {
			logger.debug("Calibration socket error", e);
			return CalibrationResult.timeout();
		}
	}

	/** Aborts a running calibration by sending a halt to the primary interface. */
	public void stop() {
		sender.sendRawPrimary("halt\n");
	}

	private String buildProgram(GiaTcp tcp, double[] refTcpPoseSi, boolean errInterrupt, int debugLvl) {
		CalibParams p = tcp.params;
		double accRad = p.accelMmS2 / 1000.0;
		double velRad = p.speedMmS / 1000.0;
		int id = tcp.id;
		String tcpPose = UrScript.pose(refTcpPoseSi);
		String center = UrScript.pose(tcp.centerPose);
		// Degrees: gia__adjustAngleXY applies d2r() internally (same convention as accuracy).
		String maxAngle = String.format(Locale.US, "[%.4f,%.4f]", p.maxAngleRxDeg, p.maxAngleRyDeg);
		// Wide tolerances during installation referencing so it does not fail on limits.
		String tolMin = "[-999,-999,-999,-999]";
		String tolMax = "[999,999,999,999]";

		StringBuilder sb = new StringBuilder();
		sb.append(ScriptLibrary.build(errInterrupt, debugLvl, true, "127.0.0.1", Const.CALIB_RETURN_PORT));
		sb.append('\n');
		sb.append("set_tcp(").append(tcpPose).append(")\n");
		sb.append("gia__cRef = ").append(center).append('\n');
		sb.append("gia__cStart = ").append(center).append('\n');
		sb.append("movel(gia__cStart, a=").append(UrScript.num(accRad)).append(", v=").append(UrScript.num(velRad)).append(")\n");
		sb.append("gia_tcp_initCalib(").append(id).append(")\n");
		sb.append("gia_tcp_calibXYZ(").append(id).append(", ").append(tcpPose)
				.append(", gia__cRef, gia__cStart, ")
				.append(tcp.ioX).append(", ").append(tcp.ioY).append(", ")
				.append(UrScript.num(p.radiusMm)).append(", ")
				.append(UrScript.num(accRad)).append(", ").append(UrScript.num(velRad)).append(", ")
				.append(UrScript.num(p.overrunDeg)).append(", ")
				.append(UrScript.num(p.signedSearchZMm())).append(", ")
				.append(UrScript.num(p.signedImmerseZMm(Const.DEF_IMMERSEZ_MM))).append(", ")
				// Referencing stores the RAW measured diameter (diamOffset 0, as CAPTRON):
				// the real-vs-measured bias is applied later, at runtime, as real - stored.
				.append("0, ")
				.append(tolMin).append(", ").append(tolMax).append(")\n");
		if (p.adjustAngle) {
			sb.append("if (gia_tcp_isActionOk(").append(id).append(")):\n");
			sb.append("  gia_tcp_calibAngleXY(").append(id).append(", gia__cRef, ")
					.append(tcp.ioX).append(", ").append(tcp.ioY).append(", ")
					.append(UrScript.num(p.radiusMm)).append(", ")
					.append(UrScript.num(accRad)).append(", ").append(UrScript.num(velRad)).append(", ")
					.append(UrScript.num(p.overrunDeg)).append(", ")
					.append(p.iterator).append(", ")
					.append(UrScript.num(p.accuracyDeg)).append(", ")
					.append(UrScript.num(p.offsetZMm)).append(", ")
					.append(maxAngle).append(")\n");
			sb.append("end\n");
		}
		// Report status + correction (mm / deg) + diameter (mm) back to the URCap.
		sb.append("gia__st = gia_tcp_getStatus(").append(id).append(")\n");
		sb.append("gia__cc = gia_tcp_getCalibCorrection(").append(id).append(")\n");
		sb.append("gia__dd = gia_tcp_getDiameterMM(").append(id).append(")\n");
		sb.append("gia__msg = to_str(gia__st) + \",\" + to_str(gia__cc[0]*1000) + \",\" + to_str(gia__cc[1]*1000) + \",\" + to_str(gia__cc[2]*1000) + \",\" + to_str(r2d(gia__cc[3])) + \",\" + to_str(r2d(gia__cc[4])) + \",\" + to_str(r2d(gia__cc[5])) + \",\" + to_str(gia__dd)\n");
		sb.append("gia__reportResult(gia__msg)\n");
		return sb.toString();
	}
}
