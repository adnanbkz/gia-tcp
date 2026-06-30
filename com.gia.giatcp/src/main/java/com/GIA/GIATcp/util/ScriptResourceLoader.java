package com.GIA.GIATcp.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the bundled URScript library from /scripts and substitutes {{tokens}}.
 * The library is concatenated in dependency order: global helpers, motion threads,
 * interrupt detection, angle adjustment, then the public gia_tcp_* API.
 */
public final class ScriptResourceLoader {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private static final String[] LIBRARY_ORDER = {
			"/scripts/gl.script",
			"/scripts/search_z.script",
			"/scripts/interrupt_points.script",
			"/scripts/adjust_angle.script",
			"/scripts/inst.script"
	};

	private ScriptResourceLoader() {
	}

	/** Reads and concatenates the whole calibration library, applying token substitution. */
	public static String loadLibrary(Map<String, String> tokens) {
		StringBuilder sb = new StringBuilder();
		for (String resource : LIBRARY_ORDER) {
			sb.append(substitute(readResource(resource), tokens)).append('\n');
		}
		return sb.toString();
	}

	private static String readResource(String resource) {
		try (InputStream in = ScriptResourceLoader.class.getResourceAsStream(resource)) {
			if (in == null) {
				logger.error("Script resource not found: {}", resource);
				return "";
			}
			StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line).append('\n');
				}
			}
			return sb.toString();
		} catch (IOException e) {
			logger.error("Failed to read script resource {}", resource, e);
			return "";
		}
	}

	private static String substitute(String content, Map<String, String> tokens) {
		String result = content;
		for (Map.Entry<String, String> e : tokens.entrySet()) {
			result = result.replace("{{" + e.getKey() + "}}", e.getValue());
		}
		return result;
	}

	/** Returns a token map with the localized status/error messages pre-filled. */
	public static Map<String, String> defaultMessageTokens() {
		Map<String, String> t = new LinkedHashMap<String, String>();
		t.put("err_code_success", "Success");
		t.put("err_code_1", "Wrong number of interrupt points");
		t.put("err_code_2", "Unable to calculate intersect point");
		t.put("err_code_3", "Invalid intersect point");
		t.put("err_code_4", "Input low on intersect position");
		t.put("err_code_5", "Search motion Z failed, input not low");
		t.put("err_code_6", "TCP correction outside tolerance");
		t.put("err_code_7", "TCP diameter outside tolerance");
		t.put("err_code_11", "Wrong number of interrupt points (adjust angle)");
		t.put("err_code_12", "Unable to calculate intersect point (adjust angle)");
		t.put("err_code_13", "Invalid intersect point (adjust angle)");
		t.put("err_code_14", "Adjust angle outside tolerance");
		t.put("err_code_21", "Input low on reference position (check)");
		t.put("err_code_31", "Immerse motion Z failed, inputs not high");
		t.put("err_code_unknown", "Unknown");
		return t;
	}
}
