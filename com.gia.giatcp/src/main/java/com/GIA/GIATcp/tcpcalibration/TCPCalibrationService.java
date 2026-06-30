package com.GIA.GIATcp.tcpcalibration;
import com.GIA.GIATcp.tcpcalibration.math.TCPCalibrationMaths;

import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.ContributionConfiguration;
import com.ur.urcap.api.contribution.program.CreationContext;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeService;
import com.ur.urcap.api.domain.data.DataModel;

import java.util.Locale;

/**
 * New, self-contained TCP-calibration program node. Unlike the legacy GIA TCP action,
 * all geometry runs in Java ({@link TCPCalibrationMaths}); the robot only does the
 * realtime moveC + sensor capture. Built in parallel with the old node.
 */
public class TCPCalibrationService implements SwingProgramNodeService<TCPCalibrationContribution, TCPCalibrationView> {

	static final String NODE_ID = "com.GIA.GIATcp.tcpcalibration";
	static final String NODE_TITLE = "GIA TCP";

	@Override
	public String getId() {
		return NODE_ID;
	}

	@Override
	public void configureContribution(ContributionConfiguration configuration) {
		configuration.setUserInsertable(true);
		// Required so the node can host its auto-inserted "If Error" child; without
		// this, root.addChild(...) throws TreeStructureException and the child silently
		// never appears in the program tree.
		configuration.setChildrenAllowed(true);
	}

	@Override
	public String getTitle(Locale locale) {
		return NODE_TITLE;
	}

	@Override
	public TCPCalibrationView createView(ViewAPIProvider apiProvider) {
		return new TCPCalibrationView(apiProvider);
	}

	@Override
	public TCPCalibrationContribution createNode(ProgramAPIProvider apiProvider, TCPCalibrationView view,
			DataModel model, CreationContext context) {
		return new TCPCalibrationContribution(apiProvider, view, model);
	}
}
