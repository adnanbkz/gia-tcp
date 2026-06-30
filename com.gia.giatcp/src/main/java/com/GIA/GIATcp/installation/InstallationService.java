package com.GIA.GIATcp.installation;

import com.GIA.GIATcp.util.Const;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.installation.ContributionConfiguration;
import com.ur.urcap.api.contribution.installation.CreationContext;
import com.ur.urcap.api.contribution.installation.InstallationAPIProvider;
import com.ur.urcap.api.contribution.installation.swing.SwingInstallationNodeService;
import com.ur.urcap.api.domain.data.DataModel;

import java.util.Locale;

public class InstallationService implements SwingInstallationNodeService<InstallationContribution, InstallationView> {

	@Override
	public void configureContribution(ContributionConfiguration configuration) {
	}

	@Override
	public String getTitle(Locale locale) {
		return Const.INSTALL_TITLE;
	}

	@Override
	public InstallationView createView(ViewAPIProvider apiProvider) {
		return new InstallationView();
	}

	@Override
	public InstallationContribution createInstallationNode(InstallationAPIProvider apiProvider, InstallationView view,
			DataModel model, CreationContext context) {
		return new InstallationContribution(apiProvider, view, model, context);
	}
}
