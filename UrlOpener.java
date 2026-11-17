package com.foxclient.util;

import com.foxclient.FoxClient;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/**
 * Opens a URL in the user's browser.
 *
 * <p>26.1 removed the {@code Util.getPlatform().openUri(...)} helper this used to call, so the
 * desktop is asked directly: the platform's own opener ({@code xdg-open}, {@code open},
 * {@code rundll32}) first, then {@code java.awt.Desktop} as a fallback. Both are wrapped: a missing
 * opener must never take the game down, it just reports false and the caller shows the URL to copy.</p>
 */
public final class UrlOpener {

	private UrlOpener() {
	}

	public static boolean open(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		try {
			ProcessBuilder builder;
			if (os.contains("win")) {
				builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
			} else if (os.contains("mac")) {
				builder = new ProcessBuilder("open", url);
			} else {
				builder = new ProcessBuilder("xdg-open", url);
			}
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			builder.start();
			return true;
		} catch (Exception first) {
			try {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
					Desktop.getDesktop().browse(URI.create(url));
					return true;
				}
			} catch (Throwable second) {
				FoxClient.LOGGER.warn("[FoxClientv2.0] could not open a browser: {}", second.toString());
			}
			return false;
		}
	}
}
