package com.GIA.GIATcp.impl;

import com.GIA.GIATcp.installation.InstallationService;
import com.GIA.GIATcp.program.action.ProgTcpActionService;
import com.GIA.GIATcp.program.errhandling.ProgErrHandlingService;
import com.GIA.GIATcp.tcpcalibration.probe.CalibrationServer;
import com.GIA.GIATcp.tcpcalibration.TCPCalibrationService;
import com.ur.urcap.api.contribution.installation.swing.SwingInstallationNodeService;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for the GIA TCP Calibrator URCap.
 *
 * Registers the installation node (the TCP setup/overview page) and the GIA TCP
 * calibration program node.
 *
 * The legacy program node (the old GIA TCP action node + its auto-inserted "If Error"
 * child) is kept in the source tree but its registration is disabled below — the new
 * calibration node supersedes it. Re-enable by uncommenting the two lines.
 */
public class Activator implements BundleActivator {

	/** Persistent server that runs the calibration maths when a program calibrates at runtime. */
	private final CalibrationServer calibrationServer = new CalibrationServer();

	@Override
	public void start(BundleContext bundleContext) {
		calibrationServer.start();

		bundleContext.registerService(SwingInstallationNodeService.class, new InstallationService(), null);
		bundleContext.registerService(SwingProgramNodeService.class, new TCPCalibrationService(), null);
		// "If Error" child node, auto-inserted under the GIA TCP node for error handling.
		bundleContext.registerService(SwingProgramNodeService.class, new ProgErrHandlingService(), null);

		// --- Legacy GIA TCP action node (disabled, superseded by TCPCalibrationService). Uncomment to re-enable: ---
		// bundleContext.registerService(SwingProgramNodeService.class, new ProgTcpActionService(), null);
	}

	@Override
	public void stop(BundleContext bundleContext) {
		calibrationServer.stop();
	}
}
