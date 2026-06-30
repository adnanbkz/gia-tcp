package com.GIA.GIATcp.program.errhandling;

import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Const;
import com.ur.urcap.api.contribution.ProgramNodeContribution;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.domain.ProgramAPI;
import com.ur.urcap.api.domain.data.DataModel;
import com.ur.urcap.api.domain.program.ProgramModel;
import com.ur.urcap.api.domain.program.nodes.ProgramNodeFactory;
import com.ur.urcap.api.domain.program.nodes.builtin.FolderNode;
import com.ur.urcap.api.domain.program.structure.TreeNode;
import com.ur.urcap.api.domain.program.structure.TreeStructureException;
import com.ur.urcap.api.domain.script.ScriptWriter;
import com.ur.urcap.api.domain.undoredo.UndoRedoManager;
import com.ur.urcap.api.domain.undoredo.UndoableChanges;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

/**
 * Container node for user-defined error-handling logic. Its script is the script
 * of its child nodes; the parent action node decides when this subtree runs.
 *
 * <p>To mirror CAPTRON's "If Error" layout, it auto-inserts one empty {@code FolderNode}
 * named "Insert your error handling here" the first time the node is opened, so the
 * user has an obvious place to drop recovery nodes. The folder is transparent to
 * script generation (it just emits its own children).</p>
 */
public class ProgErrHandlingContribution implements ProgramNodeContribution {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private final ProgramModel programModel;
	private final UndoRedoManager undoRedoManager;
	private final Texts texts;
	private final DataModel model;

	public ProgErrHandlingContribution(ProgramAPIProvider apiProvider, ProgErrHandlingView view, DataModel model) {
		ProgramAPI programAPI = apiProvider.getProgramAPI();
		this.programModel = programAPI.getProgramModel();
		this.undoRedoManager = programAPI.getUndoRedoManager();
		this.texts = Texts.from(apiProvider.getSystemAPI().getSystemSettings().getLocalization());
		this.model = model;
	}

	/**
	 * Inserts the "Insert your error handling here" placeholder folder exactly once,
	 * the first time the view opens (when {@code getRootTreeNode(this)} is valid for
	 * mutation). The flag prevents re-inserting it if the user later removes it.
	 */
	private void ensurePlaceholderFolder() {
		if (model.get(Const.K_ERR_FOLDER_DONE, false)) {
			return;
		}
		undoRedoManager.recordChanges(new UndoableChanges() {
			@Override
			public void executeChanges() {
				try {
					TreeNode root = programModel.getRootTreeNode(ProgErrHandlingContribution.this);
					if (!root.getChildren().isEmpty()) {
						model.set(Const.K_ERR_FOLDER_DONE, true);
						return;
					}
					ProgramNodeFactory factory = programModel.getProgramNodeFactory();
					FolderNode folder = factory.createFolderNode();
					folder.setName(texts.t("ERR_FOLDER_NAME"));
					root.addChild(folder);
					model.set(Const.K_ERR_FOLDER_DONE, true);
				} catch (TreeStructureException e) {
					// Tree not ready for mutation (program running / locked): retry next openView.
					logger.debug("Could not insert error-handling placeholder folder", e);
				} catch (RuntimeException e) {
					logger.debug("Unexpected error inserting error-handling placeholder folder", e);
				}
			}
		});
	}

	@Override
	public void openView() {
		ensurePlaceholderFolder();
	}

	@Override
	public void closeView() {
	}

	@Override
	public String getTitle() {
		return Const.ERR_TITLE;
	}

	@Override
	public boolean isDefined() {
		return true;
	}

	@Override
	public void generateScript(ScriptWriter writer) {
		writer.writeChildren();
	}
}
