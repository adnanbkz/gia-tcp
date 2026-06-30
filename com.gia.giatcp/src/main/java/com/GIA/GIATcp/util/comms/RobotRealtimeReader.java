package com.GIA.GIATcp.util.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Reads single snapshots from the UR real-time client interface (port 30003) to
 * obtain the live actual TCP pose. Read-only and non-intrusive: it never sends
 * anything to the robot, so it can be polled while no program is running.
 *
 * <p>Ported (trimmed) from the GIAWeld {@code RobotRealtimeReader}; only the
 * actual-TCP-pose field is decoded here. Socket I/O blocks, so call {@link
 * #readNow()} off the Swing EDT.</p>
 */
public final class RobotRealtimeReader {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private static final int RT_PORT = 30003;
	private static final int CONNECT_TIMEOUT_MS = 1000;
	private static final int READ_TIMEOUT_MS = 1000;

	// TCP_actual (actual Cartesian TCP pose in base frame) starts at double index 56,
	// length 6, per the UR real-time client interface specification.
	private static final int TCP_ACTUAL_INDEX = 56;
	private static final int TCP_ACTUAL_COUNT = 6;

	private final String robotIp;
	private double[] message;

	public RobotRealtimeReader() {
		this("127.0.0.1");
	}

	public RobotRealtimeReader(String robotIp) {
		this.robotIp = robotIp;
	}

	/** Reads one snapshot. Quiet on failure (leaves the previous snapshot/null in place). */
	public void readNow() {
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(robotIp, RT_PORT), CONNECT_TIMEOUT_MS);
			socket.setSoTimeout(READ_TIMEOUT_MS);
			DataInputStream in = new DataInputStream(socket.getInputStream());
			int length = in.readInt();
			if (length <= 4 || length > 8192) {
				return;
			}
			double[] buf = new double[length];
			buf[0] = length;
			int available = (length - 4) / 8;
			for (int i = 1; i <= available; i++) {
				buf[i] = in.readDouble();
			}
			message = buf;
		} catch (IOException e) {
			logger.debug("Realtime read from {}:{} failed", robotIp, RT_PORT, e);
		} finally {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * @return actual TCP pose [x,y,z,rx,ry,rz] in SI units (m, rad), or {@code null}
	 *         if no valid snapshot has been read yet.
	 */
	public double[] getActualTcpPose() {
		double[] snapshot = message;
		if (snapshot == null || snapshot.length < TCP_ACTUAL_INDEX + TCP_ACTUAL_COUNT) {
			return null;
		}
		double[] pose = new double[TCP_ACTUAL_COUNT];
		System.arraycopy(snapshot, TCP_ACTUAL_INDEX, pose, 0, TCP_ACTUAL_COUNT);
		return pose;
	}
}
