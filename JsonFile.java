package com.foxclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.foxclient.FoxClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Small, boring JSON file helper for the two features that keep data of their own (macro slots and
 * ghost runs).
 *
 * <p>It exists so neither of those has to grow its own copy of "make the folder, build a writer,
 * catch the exception" — and, more importantly, so a corrupt file can never take the client down:
 * read failures return {@code null} and the caller starts empty, write failures are logged once and
 * the feature keeps running in memory. Files live under {@code config/foxclient/} next to
 * {@code config.json}, with a timestamped {@code .bak} when a file cannot be parsed, so a user can
 * look at what broke instead of losing it.</p>
 */
public final class JsonFile {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private JsonFile() {
	}

	/** Directory every FoxClient data file lives in. Created on demand. */
	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(FoxClient.MOD_ID);
	}

	public static Path path(String fileName) {
		return folder().resolve(fileName);
	}

	/**
	 * Reads a JSON object, or {@code null} when the file is missing, empty or not an object. A file
	 * that parses badly is moved aside rather than deleted.
	 */
	public static JsonObject read(String fileName) {
		Path path = path(fileName);
		if (!Files.exists(path)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement element = JsonParser.parseReader(reader);
			if (element != null && element.isJsonObject()) {
				return element.getAsJsonObject();
			}
			FoxClient.LOGGER.warn("[FoxClientv2.0] {} is not a JSON object, ignoring it", fileName);
			return null;
		} catch (Exception e) {
			FoxClient.LOGGER.warn("[FoxClientv2.0] could not read {}: {}", fileName, e.toString());
			backup(path);
			return null;
		}
	}

	/** Writes an object, creating folders as needed. Never throws. */
	public static void write(String fileName, JsonObject object) {
		Path path = path(fileName);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(object, writer);
			}
		} catch (IOException e) {
			FoxClient.LOGGER.warn("[FoxClientv2.0] could not write {}: {}", fileName, e.toString());
		}
	}

	private static void backup(Path path) {
		try {
			Path backup = path.resolveSibling(path.getFileName() + ".bak-"
					+ LocalDateTime.now().format(STAMP));
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
			FoxClient.LOGGER.warn("[FoxClientv2.0] unreadable file moved to {}", backup.getFileName());
		} catch (IOException ignored) {
			// nothing left to try; the caller starts from an empty state either way
		}
	}
}
