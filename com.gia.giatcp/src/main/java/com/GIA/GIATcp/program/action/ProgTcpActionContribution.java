package com.GIA.GIATcp.program.action;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.model.CalibParams;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.program.errhandling.ProgErrHandlingService;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.UrScript;
import com.ur.urcap.api.contribution.ProgramNodeContribution;
import com.ur.urcap.api.contribution.program.CreationContext;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.domain.ProgramAPI;
import com.ur.urcap.api.domain.data.DataModel;
import com.ur.urcap.api.domain.program.ProgramModel;
import com.ur.urcap.api.domain.program.nodes.ProgramNodeFactory;
import com.ur.urcap.api.domain.program.nodes.contributable.URCapProgramNode;
import com.ur.urcap.api.domain.program.structure.TreeNode;
import com.ur.urcap.api.domain.program.structure.TreeStructureException;
import com.ur.urcap.api.domain.script.ScriptWriter;
import com.ur.urcap.api.domain.undoredo.UndoRedoManager;
import com.ur.urcap.api.domain.undoredo.UndoableChanges;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Locale;

public class ProgTcpActionContribution implements ProgramNodeContribution {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private final ProgramAPIProvider apiProvider;
	private final ProgramAPI programAPI;
	private final ProgramModel programModel;
	private final UndoRedoManager undoRedoManager;
	private final ProgTcpActionView view;
	private final DataModel model;

	public ProgTcpActionContribution(ProgramAPIProvider apiProvider, ProgTcpActionView view, DataModel model,
			CreationContext context) {
		this.apiProvider = apiProvider;
		this.programAPI = apiProvider.getProgramAPI();
		this.programModel = programAPI.getProgramModel();
		this.undoRedoManager = programAPI.getUndoRedoManager();
		this.view = view;
		this.model = model;
	}

	/**
	 * Inserts the "If Error" child node exactly once, the first time the node's view
	 * is opened. Done here (not in the constructor) so the node is already part of the
	 * program tree and {@code getRootTreeNode(this)} is valid; the flag prevents
	 * re-adding it if the user deletes it.
	 */
	private void ensureErrorHandlingChild() {
		if (model.get(Const.K_ACT_CHILD_DONE, false)) {
			return;
		}
		undoRedoManager.recordChanges(new UndoableChanges() {
			@Override
			public void executeChanges() {
				try {
					TreeNode root = programModel.getRootTreeNode(ProgTcpActionContribution.this);
					if (!root.getChildren().isEmpty()) {
						model.set(Const.K_ACT_CHILD_DONE, true);
						return;
					}
					ProgramNodeFactory factory = programModel.getProgramNodeFactory();
					URCapProgramNode errNode = factory.createURCapProgramNode(ProgErrHandlingService.class);
					root.addChild(errNode);
					model.set(Const.K_ACT_CHILD_DONE, true);
				} catch (TreeStructureException e) {
					// Tree not ready for mutation (e.g. program running / locked): retry next openView.
					logger.debug("Could not insert If Error child node", e);
				} catch (RuntimeException e) {
					// Never let a tree-mutation failure surface as a node error in PolyScope.
					logger.debug("Unexpected error inserting If Error child node", e);
				}
			}
		});
	}

	/**
	 * Wraps a data-model mutation in an UndoableChanges scope. PolyScope requires
	 * every program-tree / data-model change to be recorded, otherwise it throws
	 * IllegalStateException. All setters below go through this so changes from Swing
	 * controls (radios, combos, checkboxes) are valid, just like the keypad fields.
	 */
	private void edit(final Runnable change) {
		undoRedoManager.recordChanges(new UndoableChanges() {
			@Override
			public void executeChanges() {
				change.run();
			}
		});
	}

	// ---------------- accessors used by the view ----------------

	public InstallationContribution getInstallation() {
		return programAPI.getInstallationNode(InstallationContribution.class);
	}

	public int getTcpId() {
		return model.get(Const.K_ACT_TCPID, firstTcpId());
	}

	public void setTcpId(final int id) {
		edit(() -> model.set(Const.K_ACT_TCPID, id));
	}

	private int firstTcpId() {
		InstallationContribution inst = getInstallation();
		if (inst != null) {
			int first = inst.getStore().firstExistingId();
			return first == 0 ? 1 : first;
		}
		return 1;
	}

	public int getAction() {
		return model.get(Const.K_ACT_ACTION, Const.ACTION_CHECK);
	}

	public void setAction(final int action) {
		edit(() -> model.set(Const.K_ACT_ACTION, action));
	}

	public int getSpeed() {
		return model.get(Const.K_ACT_SPEED, 1);
	}

	public void setSpeed(final int speed) {
		edit(() -> model.set(Const.K_ACT_SPEED, speed));
	}

	public double getApproachZ() {
		return model.get(Const.K_ACT_APPROACHZ, Const.DEF_APPROACHZ_MM);
	}

	public void setApproachZ(final double v) {
		edit(() -> model.set(Const.K_ACT_APPROACHZ, v));
	}

	public double getImmerseZ() {
		return model.get(Const.K_ACT_IMMERSEZ, Const.DEF_IMMERSEZ_MM);
	}

	public void setImmerseZ(final double v) {
		edit(() -> model.set(Const.K_ACT_IMMERSEZ, v));
	}

	public boolean isSetTcpAfter() {
		return model.get(Const.K_ACT_SET_AFTER, true);
	}

	public void setSetTcpAfter(final boolean v) {
		edit(() -> model.set(Const.K_ACT_SET_AFTER, v));
	}

	public boolean isAdjustAngle() {
		return model.get(Const.K_ACT_ADJANGLE, false);
	}

	public void setAdjustAngle(final boolean v) {
		edit(() -> model.set(Const.K_ACT_ADJANGLE, v));
	}

	public int getIterator() {
		return model.get(Const.K_ACT_ITER, Const.DEF_ITER);
	}

	public void setIterator(final int v) {
		edit(() -> model.set(Const.K_ACT_ITER, v));
	}

	public double getOffsetZ() {
		return model.get(Const.K_ACT_OFFZ, Const.DEF_OFFZ_MM);
	}

	public void setOffsetZ(final double v) {
		edit(() -> model.set(Const.K_ACT_OFFZ, v));
	}

	public boolean isErrHandling() {
		return model.get(Const.K_ACT_ERRH, true);
	}

	public void setErrHandling(final boolean v) {
		edit(() -> model.set(Const.K_ACT_ERRH, v));
	}

	public boolean isUseCustomVar() {
		return model.get(Const.K_ACT_USE_CUSTOM_VAR, false);
	}

	public void setUseCustomVar(final boolean v) {
		edit(() -> model.set(Const.K_ACT_USE_CUSTOM_VAR, v));
	}

	public String getCustomVar() {
		return model.get(Const.K_ACT_CUSTOM_VAR, "");
	}

	public void setCustomVar(final String v) {
		edit(() -> model.set(Const.K_ACT_CUSTOM_VAR, v));
	}

	/** Symmetric +/- allowed deviation (mm) for an axis in {X,Y,Z,D}. */
	public double getTol(String axis) {
		return model.get(String.format(Const.K_TOL, axis), Const.DEF_TOL_MM);
	}

	public void setTol(final String axis, final double v) {
		edit(() -> model.set(String.format(Const.K_TOL, axis), Math.abs(v)));
	}

	// ---------------- lifecycle ----------------

	@Override
	public void openView() {
		ensureErrorHandlingChild();
		view.refresh(this);
	}

	/** Program-scoped variable display names, for the custom-assignment combo. */
	public java.util.List<String> getVariableNames() {
		java.util.List<String> names = new java.util.ArrayList<String>();
		for (com.ur.urcap.api.domain.variable.Variable v : programAPI.getVariableModel().getAll()) {
			names.add(v.getDisplayName());
		}
		return names;
	}

	@Override
	public void closeView() {
	}

	@Override
	public String getTitle() {
		return Const.ACTION_TITLE;
	}

	@Override
	public boolean isDefined() {
		InstallationContribution inst = getInstallation();
		return inst != null && inst.getStore().exists(getTcpId());
	}

	@Override
	public void generateScript(ScriptWriter writer) {
		InstallationContribution inst = getInstallation();
		if (inst == null || !inst.getStore().exists(getTcpId())) {
			writer.appendLine("popup(\"GIA TCP: invalid TCP setup\", title=\"GIA TCP\", blocking=True)");
			writer.appendLine("halt");
			return;
		}
		GiaTcp tcp = inst.getStore().load(getTcpId());
		double[] refPose = inst.resolveTcpPoseSi(tcp.refTcp);
		CalibParams p = tcp.params;

		double factor = getSpeed() == 0 ? 0.5 : (getSpeed() == 2 ? 1.5 : 1.0);
		double accRad = p.accelMmS2 / 1000.0;
		double velRad = (p.speedMmS * factor) / 1000.0;
		String acc = UrScript.num(accRad);
		String vel = UrScript.num(velRad);
		int id = tcp.id;
		String refTcp = UrScript.pose(refPose);
		String center = UrScript.pose(tcp.centerPose);
		String immerseZ = UrScript.num(getImmerseZ());
		// Symmetric band: each axis passes when its deviation is within +/- the tolerance.
		String tolMin = String.format(Locale.US, "[%s,%s,%s,%s]",
				mm2m(-getTol("X")), mm2m(-getTol("Y")), mm2m(-getTol("Z")), mm2m(-getTol("D")));
		String tolMax = String.format(Locale.US, "[%s,%s,%s,%s]",
				mm2m(getTol("X")), mm2m(getTol("Y")), mm2m(getTol("Z")), mm2m(getTol("D")));

		writer.appendLine("# --- GIA TCP ---");

		switch (getAction()) {
			case Const.ACTION_CHECK:
				emitCheck(writer, tcp, id, acc, vel, immerseZ);
				break;
			case Const.ACTION_VALIDATE:
				emitCalibMotion(writer, tcp, id, refTcp, center, acc, vel, immerseZ, tolMin, tolMax);
				break;
			case Const.ACTION_RECALIBRATE:
			default:
				emitCalibMotion(writer, tcp, id, refTcp, center, acc, vel, immerseZ, tolMin, tolMax);
				emitAssignment(writer, id);
				break;
		}

		if (isErrHandling()) {
			writer.ifCondition("not gia_tcp_isActionOk(" + id + ")");
			writer.writeChildren();
			writer.end();
		}
	}

	private void emitCheck(ScriptWriter writer, GiaTcp tcp, int id, String acc, String vel, String immerseZ) {
		// Start optimistic (status 0); immerse / checkCalib set it on failure.
		writer.appendLine("gia_tcp_initCalib(" + id + ", 0)");
		writer.appendLine("gia_tcp_activateTCP(" + id + ")");
		writer.appendLine("gia__chkRef = " + UrScript.pose(tcp.centerPose));
		writer.appendLine("movel(gia__chkRef, a=" + acc + ", v=" + vel + ")");
		writer.appendLine("if (not gia__getInput(" + tcp.ioX + ")) or (not gia__getInput(" + tcp.ioY + ")):");
		writer.appendLine("  gia_tcp_immerseZ(" + id + ", gia__chkRef, " + tcp.ioX + ", " + tcp.ioY + ", " + acc + ", " + vel + ", " + immerseZ + ")");
		writer.appendLine("end");
		writer.appendLine("if gia_tcp_isActionOk(" + id + "):");
		writer.appendLine("  gia_tcp_checkCalib(" + id + ", " + tcp.ioX + ", " + tcp.ioY + ")");
		writer.appendLine("end");
	}

	private void emitCalibMotion(ScriptWriter writer, GiaTcp tcp, int id, String refTcp, String center,
			String acc, String vel, String immerseZ, String tolMin, String tolMax) {
		CalibParams p = tcp.params;
		writer.appendLine("gia_tcp_initCalib(" + id + ")");
		writer.appendLine("set_tcp(" + refTcp + ")");
		writer.appendLine("gia__aRef = " + center);
		writer.appendLine("gia__aStart = pose_trans(gia__aRef, p[0,0," + UrScript.num(getApproachZ() / 1000.0) + ",0,0,0])");
		writer.appendLine("movel(gia__aStart, a=" + acc + ", v=" + vel + ")");
		writer.appendLine("gia_tcp_calibXYZ(" + id + ", " + refTcp + ", gia__aRef, gia__aStart, "
				+ tcp.ioX + ", " + tcp.ioY + ", " + UrScript.num(p.radiusMm) + ", " + acc + ", " + vel + ", "
				+ UrScript.num(p.overrunDeg) + ", " + UrScript.num(p.signedSearchZMm()) + ", " + immerseZ + ", "
				+ UrScript.num(p.realDiameterMm) + ", " + tolMin + ", " + tolMax + ")");
		if (isAdjustAngle()) {
			// Degrees: gia__adjustAngleXY applies d2r() internally (same convention as accuracy).
			String maxAngle = String.format(Locale.US, "[%.4f,%.4f]", p.maxAngleRxDeg, p.maxAngleRyDeg);
			writer.appendLine("if gia_tcp_isActionOk(" + id + "):");
			writer.appendLine("  gia_tcp_calibAngleXY(" + id + ", gia__aRef, " + tcp.ioX + ", " + tcp.ioY + ", "
					+ UrScript.num(p.radiusMm) + ", " + acc + ", " + vel + ", " + UrScript.num(p.overrunDeg) + ", "
					+ getIterator() + ", " + UrScript.num(p.accuracyDeg) + ", " + UrScript.num(getOffsetZ()) + ", " + maxAngle + ")");
			writer.appendLine("end");
		}
	}

	private void emitAssignment(ScriptWriter writer, int id) {
		String var = isUseCustomVar() && !getCustomVar().isEmpty() ? getCustomVar() : Const.DEFAULT_RECALIB_VAR;
		writer.appendLine("if gia_tcp_isActionOk(" + id + "):");
		writer.appendLine("  global " + var + " = gia_tcp_getCalibTCP(" + id + ")");
		if (isSetTcpAfter()) {
			writer.appendLine("  gia_tcp_activateTCP(" + id + ")");
		}
		writer.appendLine("end");
	}

	private static String mm2m(double mm) {
		return UrScript.num(mm / 1000.0);
	}
}
