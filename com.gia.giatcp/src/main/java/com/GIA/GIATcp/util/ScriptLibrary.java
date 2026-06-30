package com.GIA.GIATcp.util;

import java.util.Map;

/**
 * Produces the substituted gia_tcp_* URScript library. Used by the installation
 * preamble (generateScript, no live reporting) and by the live calibration
 * controller (report enabled so results stream back over a socket).
 */
public final class ScriptLibrary {

	private ScriptLibrary() {
	}

	public static String build(boolean errInterrupt, int debugLvl, boolean report, String reportIp, int reportPort) {
		Map<String, String> tokens = ScriptResourceLoader.defaultMessageTokens();
		tokens.put("p0", "p[0,0,0,0,0,0]");
		tokens.put("err_interrupt", errInterrupt ? "True" : "False");
		tokens.put("debug_lvl", Integer.toString(debugLvl));
		tokens.put("report", report ? "True" : "False");
		tokens.put("report_ip", reportIp);
		tokens.put("report_port", Integer.toString(reportPort));
		return ScriptResourceLoader.loadLibrary(tokens);
	}
}
