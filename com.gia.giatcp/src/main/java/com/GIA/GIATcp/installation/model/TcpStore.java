package com.GIA.GIATcp.installation.model;

import com.GIA.GIATcp.util.Const;
import com.ur.urcap.api.domain.data.DataModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@link GiaTcp} slots to the installation {@link DataModel}.
 * Up to {@link Const#MAX_TCP} slots are addressed by 1-based id; poses are stored
 * as comma-separated SI doubles.
 */
public class TcpStore {

	private final DataModel model;

	public TcpStore(DataModel model) {
		this.model = model;
	}

	private static String k(String template, int id) {
		return String.format(template, id);
	}

	public boolean exists(int id) {
		return model.get(k(Const.K_EXISTS, id), false);
	}

	public int getSelectedId() {
		return model.get(Const.KEY_SELECTED, firstExistingId());
	}

	public void setSelectedId(int id) {
		model.set(Const.KEY_SELECTED, id);
	}

	public int firstExistingId() {
		for (int id = 1; id <= Const.MAX_TCP; id++) {
			if (exists(id)) {
				return id;
			}
		}
		return 0;
	}

	public int nextFreeId() {
		for (int id = 1; id <= Const.MAX_TCP; id++) {
			if (!exists(id)) {
				return id;
			}
		}
		return 0;
	}

	public List<GiaTcp> all() {
		List<GiaTcp> list = new ArrayList<GiaTcp>();
		for (int id = 1; id <= Const.MAX_TCP; id++) {
			if (exists(id)) {
				list.add(load(id));
			}
		}
		return list;
	}

	/** Creates a new empty slot with defaults and returns its id (0 if full). */
	public int create() {
		int id = nextFreeId();
		if (id == 0) {
			return 0;
		}
		GiaTcp tcp = new GiaTcp();
		tcp.id = id;
		tcp.exists = true;
		tcp.name = Const.DEFAULT_TCP_NAME + id;
		tcp.variant = TcpVariant.FGL50_IK;
		save(tcp);
		setSelectedId(id);
		return id;
	}

	public void delete(int id) {
		model.set(k(Const.K_EXISTS, id), false);
		if (getSelectedId() == id) {
			setSelectedId(firstExistingId());
		}
	}

	public GiaTcp load(int id) {
		GiaTcp t = new GiaTcp();
		t.id = id;
		t.exists = model.get(k(Const.K_EXISTS, id), false);
		t.name = model.get(k(Const.K_NAME, id), Const.DEFAULT_TCP_NAME + id);
		t.variant = TcpVariant.fromOrdinal(model.get(k(Const.K_VARIANT, id), 0));
		t.ioX = model.get(k(Const.K_IOX, id), -1);
		t.ioY = model.get(k(Const.K_IOY, id), -1);
		t.refTcp = model.get(k(Const.K_REF, id), "");
		t.centerPose = parsePose(model.get(k(Const.K_CENTER, id), ""));
		t.calibrated = model.get(k(Const.K_CALIBRATED, id), false);
		t.correction = parsePose(model.get(k(Const.K_CORR, id), ""));
		t.diameterMm = model.get(k(Const.K_DIAM, id), 0.0);
		t.refPose = parsePose(model.get(k(Const.K_REFPOSE, id), ""));

		CalibParams p = t.params;
		p.radiusMm = model.get(k(Const.K_RADIUS, id), Const.DEF_RADIUS_MM);
		p.speedMmS = model.get(k(Const.K_SPEED, id), Const.DEF_SPEED_MM_S);
		p.accelMmS2 = model.get(k(Const.K_ACCEL, id), Const.DEF_ACCEL_MM_S2);
		p.overrunDeg = model.get(k(Const.K_OVERRUN, id), Const.DEF_OVERRUN_DEG);
		p.searchZMm = model.get(k(Const.K_SEARCHZ, id), Const.DEF_SEARCHZ_MM);
		p.invertZ = model.get(k(Const.K_INVERTZ, id), Const.DEF_INVERTZ);
		p.realDiameterMm = model.get(k(Const.K_REALDIAM, id), Const.DEF_REALDIAM_MM);
		p.adjustAngle = model.get(k(Const.K_ADJANGLE, id), Const.DEF_ADJANGLE);
		p.iterator = model.get(k(Const.K_ITER, id), Const.DEF_ITER);
		p.offsetZMm = model.get(k(Const.K_OFFZ, id), Const.DEF_OFFZ_MM);
		p.accuracyDeg = model.get(k(Const.K_ACC, id), Const.DEF_ACCURACY_DEG);
		p.maxAngleRxDeg = model.get(k(Const.K_MAXRX, id), Const.DEF_MAXANGLE_DEG);
		p.maxAngleRyDeg = model.get(k(Const.K_MAXRY, id), Const.DEF_MAXANGLE_DEG);
		return t;
	}

	public void save(GiaTcp t) {
		model.set(k(Const.K_EXISTS, t.id), t.exists);
		model.set(k(Const.K_NAME, t.id), t.name == null ? "" : t.name);
		model.set(k(Const.K_VARIANT, t.id), t.variant.ordinal());
		model.set(k(Const.K_IOX, t.id), t.ioX);
		model.set(k(Const.K_IOY, t.id), t.ioY);
		model.set(k(Const.K_REF, t.id), t.refTcp == null ? "" : t.refTcp);
		model.set(k(Const.K_CENTER, t.id), formatPose(t.centerPose));
		model.set(k(Const.K_CALIBRATED, t.id), t.calibrated);
		model.set(k(Const.K_CORR, t.id), formatPose(t.correction));
		model.set(k(Const.K_DIAM, t.id), t.diameterMm);
		model.set(k(Const.K_REFPOSE, t.id), formatPose(t.refPose));

		CalibParams p = t.params;
		model.set(k(Const.K_RADIUS, t.id), p.radiusMm);
		model.set(k(Const.K_SPEED, t.id), p.speedMmS);
		model.set(k(Const.K_ACCEL, t.id), p.accelMmS2);
		model.set(k(Const.K_OVERRUN, t.id), p.overrunDeg);
		model.set(k(Const.K_SEARCHZ, t.id), p.searchZMm);
		model.set(k(Const.K_INVERTZ, t.id), p.invertZ);
		model.set(k(Const.K_REALDIAM, t.id), p.realDiameterMm);
		model.set(k(Const.K_ADJANGLE, t.id), p.adjustAngle);
		model.set(k(Const.K_ITER, t.id), p.iterator);
		model.set(k(Const.K_OFFZ, t.id), p.offsetZMm);
		model.set(k(Const.K_ACC, t.id), p.accuracyDeg);
		model.set(k(Const.K_MAXRX, t.id), p.maxAngleRxDeg);
		model.set(k(Const.K_MAXRY, t.id), p.maxAngleRyDeg);
	}

	public void setName(int id, String name) {
		model.set(k(Const.K_NAME, id), name);
	}

	public void setCalibrationResult(int id, double[] correction, double diameterMm, double[] measuredPose) {
		model.set(k(Const.K_CORR, id), formatPose(correction));
		model.set(k(Const.K_DIAM, id), diameterMm);
		model.set(k(Const.K_CALIBRATED, id), true);
		if (measuredPose != null) {
			model.set(k(Const.K_REFPOSE, id), formatPose(measuredPose));
		}
	}

	private static double[] parsePose(String csv) {
		double[] out = new double[6];
		if (csv == null || csv.isEmpty()) {
			return out;
		}
		String[] parts = csv.split(",");
		for (int i = 0; i < 6 && i < parts.length; i++) {
			try {
				out[i] = Double.parseDouble(parts[i].trim());
			} catch (NumberFormatException ignored) {
				out[i] = 0.0;
			}
		}
		return out;
	}

	private static String formatPose(double[] pose) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(pose != null && i < pose.length ? pose[i] : 0.0);
		}
		return sb.toString();
	}
}
