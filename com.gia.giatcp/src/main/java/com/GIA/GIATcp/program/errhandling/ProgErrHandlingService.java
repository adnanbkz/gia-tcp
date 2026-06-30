package com.GIA.GIATcp.program.errhandling;

import com.GIA.GIATcp.util.Const;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.ContributionConfiguration;
import com.ur.urcap.api.contribution.program.CreationContext;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeService;
import com.ur.urcap.api.domain.data.DataModel;

import java.util.Locale;

/**
 * The "If Error" child node of a GIA TCP action. It is not user-insertable; it is
 * created automatically under the action node and hosts the user's error-handling
 * logic, which runs only when the action reports a non-zero status.
 */
public class ProgErrHandlingService implements SwingProgramNodeService<ProgErrHandlingContribution, ProgErrHandlingView> {

	@Override
	public String getId() {
		return Const.ERR_ID;
	}

	@Override
	public void configureContribution(ContributionConfiguration configuration) {
		configuration.setUserInsertable(false);
		configuration.setChildrenAllowed(true);
	}

	@Override
	public String getTitle(Locale locale) {
		return Const.ERR_TITLE;
	}

	@Override
	public ProgErrHandlingView createView(ViewAPIProvider apiProvider) {
		return new ProgErrHandlingView(apiProvider);
	}

	@Override
	public ProgErrHandlingContribution createNode(ProgramAPIProvider apiProvider, ProgErrHandlingView view,
			DataModel model, CreationContext context) {
		return new ProgErrHandlingContribution(apiProvider, view, model);
	}
}
