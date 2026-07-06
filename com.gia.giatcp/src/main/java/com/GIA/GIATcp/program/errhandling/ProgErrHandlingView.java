package com.GIA.GIATcp.program.errhandling;

import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Ui;
import com.ur.urcap.api.contribution.ContributionProvider;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeView;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * View for the "If Error" node: explains when the children run and exposes CAPTRON's
 * error-handling mode — run the recovery nodes on every failure, or silently retry the
 * action N times first ("Try again x times").
 */
public class ProgErrHandlingView implements SwingProgramNodeView<ProgErrHandlingContribution> {

	private final ViewAPIProvider viewApiProvider;

	private final JRadioButton rImmediate = new JRadioButton();
	private final JRadioButton rRetry = new JRadioButton();
	private final JTextField fRetryCount = new JTextField(4);

	private ContributionProvider<ProgErrHandlingContribution> provider;
	private boolean updating;

	public ProgErrHandlingView(ViewAPIProvider viewApiProvider) {
		this.viewApiProvider = viewApiProvider;
	}

	@Override
	public void buildUI(JPanel panel, final ContributionProvider<ProgErrHandlingContribution> provider) {
		this.provider = provider;
		Texts t = Texts.from(viewApiProvider.getSystemAPI().getSystemSettings().getLocalization());
		KeyboardInputFactory kf = viewApiProvider.getUserInterfaceAPI().getUserInteraction()
				.getKeyboardInputFactory();
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
		content.add(Box.createVerticalStrut(14));

		rImmediate.setText(t.t("ERR_MODE_IMMEDIATE"));
		rImmediate.setAlignmentX(Component.LEFT_ALIGNMENT);
		rRetry.setText(t.t("ERR_MODE_RETRY"));
		rRetry.setAlignmentX(Component.LEFT_ALIGNMENT);
		ButtonGroup g = new ButtonGroup();
		g.add(rImmediate);
		g.add(rRetry);
		rImmediate.addActionListener(e -> set(false));
		rRetry.addActionListener(e -> set(true));
		content.add(rImmediate);
		content.add(rRetry);

		JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		countRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		countRow.add(Box.createHorizontalStrut(20));
		countRow.add(new JLabel(t.t("ERR_RETRY_COUNT")));
		Ui.wireInteger(fRetryCount, kf, v -> {
			if (!updating) {
				provider.get().setRetryCount(v);
			}
		});
		countRow.add(fRetryCount);
		content.add(countRow);

		panel.add(content, BorderLayout.NORTH);
	}

	private void set(boolean retryEnabled) {
		if (!updating && provider != null) {
			provider.get().setRetryEnabled(retryEnabled);
			fRetryCount.setEnabled(retryEnabled);
		}
	}

	public void update(ProgErrHandlingContribution c) {
		updating = true;
		boolean retry = c.isRetryEnabled();
		rImmediate.setSelected(!retry);
		rRetry.setSelected(retry);
		fRetryCount.setText(String.valueOf(c.getRetryCount()));
		fRetryCount.setEnabled(retry);
		updating = false;
	}
}
