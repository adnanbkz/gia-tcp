package com.GIA.GIATcp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads a bundled URScript resource (e.g. {@code /scripts/tcpcalib.script}) as a UTF-8 string. */
public final class ScriptResource {

	private ScriptResource() {
	}

	public static String load(String path) {
		try (InputStream in = ScriptResource.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) != -1) {
				bos.write(buf, 0, n);
			}
			return new String(bos.toByteArray(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}
}
