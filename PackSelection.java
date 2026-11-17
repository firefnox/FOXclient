package com.foxclient.util;

import com.foxclient.FoxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds and removes our built-in packs from the client's selected pack list.
 *
 * <p>The packs themselves live inside the mod jar under {@code resourcepacks/&lt;name&gt;} and are
 * registered at startup by Fabric's resource loader, so nothing is ever downloaded or unzipped. This
 * helper is the part every module that owns a pack needs: it preserves the user's other packs, only
 * ever touches our own ids, and triggers the normal client resource reload so the change lands without
 * a restart.</p>
 *
 * <p>Ids are resolved by suffix rather than assumed, because the game decides what id a built-in pack
 * gets; if a pack is not in the list (registration failed at startup) the call reports {@code false}
 * instead of silently writing a bogus id into the selection.</p>
 */
public final class PackSelection {

	private PackSelection() {
	}

	/** The ids the game gave the packs whose path ends with {@code packPath}. */
	public static String resolve(String packPath) {
		PackRepository repository = repository();
		if (repository == null) {
			return null;
		}
		for (String id : repository.getAvailableIds()) {
			if (id != null && id.endsWith(packPath)) {
				return id;
			}
		}
		return null;
	}

	public static boolean isSelected(String packPath) {
		PackRepository repository = repository();
		if (repository == null) {
			return false;
		}
		for (String id : repository.getSelectedIds()) {
			if (id != null && id.endsWith(packPath)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Makes exactly one of {@code ours} selected (or none when {@code chosen} is null), leaves every
	 * other pack alone, and reloads resources when something actually changed.
	 */
	public static boolean choose(String chosen, String... ours) {
		PackRepository repository = repository();
		if (repository == null) {
			return false;
		}

		Set<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
		boolean changed = selected.removeIf(id -> matches(id, ours));
		if (chosen != null) {
			String id = resolve(chosen);
			if (id == null) {
				FoxClient.LOGGER.warn("[FoxClientv2.0] pack '{}' is not registered - was the client started "
						+ "with this build?", chosen);
				return false;
			}
			changed |= selected.add(id);
		}
		if (!changed) {
			return true;
		}

		repository.setSelected(selected);
		Minecraft client = Minecraft.getInstance();
		try {
			if (client != null) {
				client.reloadResourcePacks();
			}
		} catch (Throwable t) {
			FoxClient.LOGGER.warn("[FoxClientv2.0] could not reload resources after a pack change", t);
		}
		return true;
	}

	public static void clear(String... ours) {
		choose(null, ours);
	}

	private static boolean matches(String id, String[] ours) {
		if (id == null || ours == null) {
			return false;
		}
		for (String path : ours) {
			if (path != null && id.endsWith(path)) {
				return true;
			}
		}
		return false;
	}

	private static PackRepository repository() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.getResourcePackRepository();
	}

	/** Names of the packs we know about, for status lines. */
	public static Collection<String> idsOf(String... ours) {
		Set<String> ids = new LinkedHashSet<>();
		for (String path : ours) {
			String id = resolve(path);
			if (id != null) {
				ids.add(id);
			}
		}
		return ids;
	}
}
