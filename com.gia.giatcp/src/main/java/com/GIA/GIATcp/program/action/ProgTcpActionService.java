package com.GIA.GIATcp.program.action;

import com.GIA.GIATcp.util.Const;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.ContributionConfiguration;
import com.ur.urcap.api.contribution.program.CreationContext;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeService;
import com.ur.urcap.api.domain.data.DataModel;

import java.util.Locale;

public class ProgTcpActionService implements SwingProgramNodeService<ProgTcpActionContribution, ProgTcpActionView> {

	@Override
	public String getId() {
		return Const.ACTION_ID;
	}

	@Override
	public void configureContribution(ContributionConfiguration configuration) {
		configuration.setChildrenAllowed(true);
	}

	@Override
	public String getTitle(Locale locale) {
		return Const.ACTION_TITLE;
	}

	@Override
	public ProgTcpActionView createView(ViewAPIProvider apiProvider) {
		return new ProgTcpActionView(apiProvider);
	}

	@Override
	public ProgTcpActionContribution createNode(ProgramAPIProvider apiProvider, ProgTcpActionView view,
			DataModel model, CreationContext context) {
		return new ProgTcpActionContribution(apiProvider, view, model, context);
	}
}
