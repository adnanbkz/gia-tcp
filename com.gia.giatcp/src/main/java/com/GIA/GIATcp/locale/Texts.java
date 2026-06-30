package com.GIA.GIATcp.locale;

import com.ur.urcap.api.domain.system.localization.Localization;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Localized UI strings for the GIA TCP Calibrator. Mirrors the GIAWeld pattern:
 * a UTF-8 {@link ResourceBundle} keyed by the pendant locale, with English as the
 * default (root) bundle. Missing keys fall back to English, and an unknown key
 * returns {@code !KEY} so it is obvious on screen.
 *
 * <p>Bundle files live in {@code src/main/resources/localizations}:
 * {@code gia-tcp.properties} (English / default) and {@code gia-tcp_es.properties}
 * (Spanish). Add more locales by dropping in {@code gia-tcp_xx.properties}.</p>
 */
public final class Texts {

	private static final String BASE = "localizations/gia-tcp";
	/** Locale whose strings live in the base bundle (so we load Locale.ROOT for it). */
	private static final Locale ROOT_LANGUAGE = Locale.ENGLISH;

	private final ResourceBundle bundle;

	public Texts(Locale locale) {
		Locale effective = (locale == null || locale.getLanguage().equals(ROOT_LANGUAGE.getLanguage()))
				? Locale.ROOT : locale;
		this.bundle = ResourceBundle.getBundle(BASE, effective, new Utf8Control());
	}

	/** Convenience factory from the UR localization service. */
	public static Texts from(Localization localization) {
		return new Texts(localization != null ? localization.getLocale() : Locale.ENGLISH);
	}

	/** Localized string for {@code key}, or {@code !key} if the key is missing. */
	public String t(String key) {
		try {
			return bundle.getString(key);
		} catch (Exception e) {
			return "!" + key;
		}
	}

	/** Localized string with {@link MessageFormat} {0}, {1}… argument substitution. */
	public String t(String key, Object... args) {
		return MessageFormat.format(t(key), args);
	}
}
