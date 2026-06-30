package com.GIA.GIATcp.util.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sends a complete primary URScript program to the primary client interface
 * (port 30001) so calibration motion runs immediately. Ported from the GIAWeld
 * TouchSensingPrimaryScriptSender.
 */
public final class PrimaryScriptSender {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private final String robotIp;

	public PrimaryScriptSender() {
		this("127.0.0.1");
	}

	public PrimaryScriptSender(String robotIp) {
		this.robotIp = robotIp;
	}

	/**
	 * Sends the program to the primary interface (30001). Returns {@code true} if the bytes
	 * were written, {@code false} if the controller could not be reached — so callers can
	 * fail fast instead of blocking on a reply that will never arrive.
	 */
	public boolean sendRawPrimary(String script) {
		if (script == null || script.trim().isEmpty()) {
			throw new IllegalArgumentException("script cannot be blank");
		}
		try (Socket socket = new Socket(robotIp, 30001);
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
			out.write(script.getBytes(StandardCharsets.US_ASCII));
			out.flush();
			return true;
		} catch (IOException e) {
			logger.warn("Unable to send primary script to {}:30001", robotIp, e);
			return false;
		}
	}
}
