package com.GIA.GIATcp.program.errhandling;

import com.GIA.GIATcp.locale.Texts;
import com.ur.urcap.api.contribution.ContributionProvider;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeView;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;

public class ProgErrHandlingView implements SwingProgramNodeView<ProgErrHandlingContribution> {

	private final ViewAPIProvider viewApiProvider;

	public ProgErrHandlingView(ViewAPIProvider viewApiProvider) {
		this.viewApiProvider = viewApiProvider;
	}

	@Override
	public void buildUI(JPanel panel, final ContributionProvider<ProgErrHandlingContribution> provider) {
		Texts t = Texts.from(viewApiProvider.getSystemAPI().getSystemSettings().getLocalization());
		// PolyScope forbids setBorder() on the root URCap panel (it throws
		// AuthorizationException), so all content goes in an inner bordered panel.
		panel.setLayout(new BorderLayout());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel heading = new JLabel(t.t("ERR_HEADING"));
		heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 14f));
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(heading);
		content.add(Box.createVerticalStrut(8));

		JLabel description = new JLabel(t.t("ERR_DESC"));
		description.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(description);

		panel.add(content, BorderLayout.NORTH);
	}

	// Kept for compatibility with the contribution's openView; the retry option is a
	// documented follow-up and is intentionally not exposed yet.
	public void setRetryEnabled(boolean enabled) {
	}

	public void setRetryCount(int count) {
	}
}
