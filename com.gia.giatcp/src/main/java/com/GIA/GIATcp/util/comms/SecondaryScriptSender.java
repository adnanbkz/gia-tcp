package com.GIA.GIATcp.util.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sends a complete URScript program to the <b>Secondary</b> client interface
 * (port 30002) for immediate execution — the same channel CAPTRON uses. The
 * script runs on the controller at the control-loop rate (realtime), so the
 * circle motion + sensor edge capture stay precise; the maths is done in Java.
 */
public final class SecondaryScriptSender {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final int SECONDARY_PORT = 30002;

	private final String robotIp;

	public SecondaryScriptSender() {
		this("127.0.0.1");
	}

	public SecondaryScriptSender(String robotIp) {
		this.robotIp = robotIp;
	}

	/**
	 * Sends the program to the secondary interface (30002). Returns {@code true} if the bytes
	 * were written, {@code false} if the controller could not be reached — so callers can
	 * fail fast instead of blocking on a reply that will never arrive.
	 */
	public boolean send(String script) {
		if (script == null || script.trim().isEmpty()) {
			throw new IllegalArgumentException("script cannot be blank");
		}
		try (Socket socket = new Socket(robotIp, SECONDARY_PORT);
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
			out.write(script.getBytes(StandardCharsets.US_ASCII));
			out.flush();
			return true;
		} catch (IOException e) {
			logger.warn("Unable to send secondary script to {}:{}", robotIp, SECONDARY_PORT, e);
			return false;
		}
	}
}
