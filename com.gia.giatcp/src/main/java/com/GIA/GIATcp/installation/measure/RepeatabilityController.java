package com.GIA.GIATcp.installation.measure;

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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a repeatability test: the same XYZ probe is executed {@code runs} times around
 * the taught centre and each run's measured correction + diameter is streamed back over
 * one loopback socket. No TCP is changed and wide tolerances are used so the raw
 * measurement is captured even if it would be out of tolerance. Sent as a primary
 * program (motion); run off the UI thread.
 */
public final class RepeatabilityController {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final int ACCEPT_TIMEOUT_MS = 120000;
	private static final int READ_TIMEOUT_MS = 120000;

	private final PrimaryScriptSender sender = new PrimaryScriptSender();

	public RepeatabilityResult run(GiaTcp tcp, double[] refTcpPoseSi, int runs, boolean errInterrupt, int debugLvl) {
		int n = Math.max(1, runs);
		String program = buildProgram(tcp, refTcpPoseSi, n, errInterrupt, debugLvl);
		try (ServerSocket server = new ServerSocket()) {
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress(Const.CALIB_RETURN_PORT));
			server.setSoTimeout(ACCEPT_TIMEOUT_MS);
			if (!sender.sendRawPrimary(program)) {
				logger.warn("Repeatability program could not be sent to the primary interface (30001)");
				return RepeatabilityResult.notReceived(n);
			}
			try (Socket socket = server.accept();
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				socket.setSoTimeout(READ_TIMEOUT_MS);
				List<double[]> successful = new ArrayList<double[]>();
				for (int i = 0; i < n; i++) {
					String line = reader.readLine();
					if (line == null) {
						break;
					}
					logger.debug("Repeatability run {}: {}", i, line);
					double[] parsed = parseSuccessful(line);
					if (parsed != null) {
						successful.add(parsed);
					}
				}
				return RepeatabilityResult.of(n, successful);
			}
		} catch (SocketTimeoutException e) {
			logger.warn("Repeatability test timed out on port {}", Const.CALIB_RETURN_PORT);
			return RepeatabilityResult.notReceived(n);
		} catch (IOException e) {
			logger.debug("Repeatability socket error", e);
			return RepeatabilityResult.notReceived(n);
		}
	}

	/** Aborts a running test by halting the primary interface. */
	public void stop() {
		sender.sendRawPrimary("halt\n");
	}

	/** Parses "run,status,xMm,yMm,zMm,diamMm"; returns [x,y,z,diam] only if status==0, else null. */
	private static double[] parseSuccessful(String line) {
		String[] t = line.split(",");
		if (t.length < 6) {
			return null;
		}
		try {
			int status = (int) Math.round(Double.parseDouble(t[1].trim()));
			if (status != 0) {
				return null;
			}
			return new double[] {
					Double.parseDouble(t[2].trim()),
					Double.parseDouble(t[3].trim()),
					Double.parseDouble(t[4].trim()),
					Double.parseDouble(t[5].trim())
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String buildProgram(GiaTcp tcp, double[] refTcpPoseSi, int runs, boolean errInterrupt, int debugLvl) {
		CalibParams p = tcp.params;
		double accRad = p.accelMmS2 / 1000.0;
		double velRad = p.speedMmS / 1000.0;
		int id = tcp.id;
		String tcpPose = UrScript.pose(refTcpPoseSi);
		String center = UrScript.pose(tcp.centerPose);
		String tolMin = "[-999,-999,-999,-999]";
		String tolMax = "[999,999,999,999]";

		StringBuilder sb = new StringBuilder();
		sb.append(ScriptLibrary.build(errInterrupt, debugLvl, false, "127.0.0.1", Const.CALIB_RETURN_PORT));
		sb.append('\n');
		sb.append("set_tcp(").append(tcpPose).append(")\n");
		sb.append("gia__cRef = ").append(center).append('\n');
		sb.append("gia__cStart = ").append(center).append('\n');
		sb.append("socket_open(\"127.0.0.1\", ").append(Const.CALIB_RETURN_PORT).append(", \"gia_meas\")\n");
		sb.append("gia__i = 0\n");
		sb.append("while (gia__i < ").append(runs).append("):\n");
		sb.append("  movel(gia__cStart, a=").append(UrScript.num(accRad)).append(", v=").append(UrScript.num(velRad)).append(")\n");
		sb.append("  gia_tcp_initCalib(").append(id).append(")\n");
		sb.append("  gia_tcp_calibXYZ(").append(id).append(", ").append(tcpPose)
				.append(", gia__cRef, gia__cStart, ")
				.append(tcp.ioX).append(", ").append(tcp.ioY).append(", ")
				.append(UrScript.num(p.radiusMm)).append(", ")
				.append(UrScript.num(accRad)).append(", ").append(UrScript.num(velRad)).append(", ")
				.append(UrScript.num(p.overrunDeg)).append(", ")
				.append(UrScript.num(p.signedSearchZMm())).append(", 0.0, ")
				.append(UrScript.num(p.realDiameterMm)).append(", ")
				.append(tolMin).append(", ").append(tolMax).append(")\n");
		sb.append("  gia__st = gia_tcp_getStatus(").append(id).append(")\n");
		sb.append("  gia__cc = gia_tcp_getCalibCorrection(").append(id).append(")\n");
		sb.append("  gia__dd = gia_tcp_getDiameterMM(").append(id).append(")\n");
		sb.append("  gia__line = to_str(gia__i) + \",\" + to_str(gia__st) + \",\" + to_str(gia__cc[0]*1000) + \",\" + to_str(gia__cc[1]*1000) + \",\" + to_str(gia__cc[2]*1000) + \",\" + to_str(gia__dd)\n");
		sb.append("  socket_send_string(gia__line, \"gia_meas\")\n");
		sb.append("  socket_send_byte(10, \"gia_meas\")\n");
		sb.append("  gia__i = gia__i + 1\n");
		sb.append("end\n");
		sb.append("socket_close(\"gia_meas\")\n");
		return sb.toString();
	}
}
