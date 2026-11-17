package com.foxclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry helpers shared by the combat modules: who is nearby, where a block could go, and how
 * to look at it.
 *
 * <p>Rotations are applied to the local player only ({@code setYRot}/{@code setXRot}). Vanilla
 * includes them in the next movement packet, which is what a server uses to validate reach — no
 * separate rotation packet is sent.</p>
 */
public final class CombatUtil {

	/** Vanilla survival reach is 4.5 blocks for blocks and 3 for entities; stay a hair inside. */
	public static final double BLOCK_REACH = 4.4;

	private CombatUtil() {
	}

	/** Nearest player that is not you, not a friend, and alive, within {@code range}. */
	public static Player nearestTarget(LocalPlayer self, double range, java.util.Set<String> friends) {
		Player best = null;
		double bestDistance = range * range;
		for (Player candidate : self.level().players()) {
			if (candidate == self || !candidate.isAlive() || candidate.isSpectator()) {
				continue;
			}
			if (friends != null && friends.contains(candidate.getName().getString().toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			double distance = self.distanceToSqr(candidate);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	/** Points the local player's view at a world position. */
	public static void lookAt(Player player, Vec3 target) {
		Vec3 eye = player.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

		player.setYRot(yaw);
		player.setXRot(pitch);
	}

	public static boolean withinReach(Player player, BlockPos pos) {
		Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		return player.getEyePosition().distanceToSqr(centre) <= BLOCK_REACH * BLOCK_REACH;
	}

	public static boolean isAir(Level level, BlockPos pos) {
		return level.getBlockState(pos).isAir();
	}

	public static boolean is(Level level, BlockPos pos, Block block) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() == block;
	}

	/** True when a crystal can sit on this block: obsidian or bedrock with air above. */
	public static boolean isCrystalBase(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() != Blocks.OBSIDIAN && state.getBlock() != Blocks.BEDROCK) {
			return false;
		}
		return level.getBlockState(pos.above()).isAir();
	}

	/**
	 * Best position to place a crystal near {@code centre}: an obsidian/bedrock block with air
	 * above, inside reach, closest to the target.
	 */
	public static BlockPos findCrystalBase(Level level, Player player, Vec3 centre, int radius) {
		return findCrystalBase(level, player, centre, radius, null);
	}

	/**
	 * Same search, skipping bases whose position is already in {@code exclude} — used when one tick
	 * places several crystals and must not pick the same obsidian twice.
	 */
	public static BlockPos findCrystalBase(Level level, Player player, Vec3 centre, int radius,
										   java.util.Set<BlockPos> exclude) {
		BlockPos origin = BlockPos.containing(centre);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (exclude != null && exclude.contains(candidate)) {
						continue;
					}
					if (!isCrystalBase(level, candidate) || !withinReach(player, candidate)) {
						continue;
					}
					double distance = centre.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 1.0,
							candidate.getZ() + 0.5);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = candidate;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Best position to place a block (anchor) near {@code centre}: an air cell with a solid
	 * neighbour to click on, inside reach, closest to the target.
	 *
	 * @return {@code [position, support]} or {@code null}
	 */
	public static BlockPos[] findPlacementCell(Level level, Player player, Vec3 centre, int radius) {
		BlockPos origin = BlockPos.containing(centre);
		BlockPos best = null;
		BlockPos bestSupport = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (!level.getBlockState(candidate).isAir() || !withinReach(player, candidate)) {
						continue;
					}
					BlockPos support = null;
					for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH,
							Direction.WEST, Direction.EAST, Direction.UP}) {
						BlockPos neighbour = candidate.relative(direction);
						if (!level.getBlockState(neighbour).isAir()) {
							support = neighbour;
							break;
						}
					}
					if (support == null) {
						continue;
					}
					double distance = centre.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5,
							candidate.getZ() + 0.5);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = candidate;
						bestSupport = support;
					}
				}
			}
		}
		return best == null ? null : new BlockPos[]{best, bestSupport};
	}

	/** Hit result aimed at the centre of the given face of a block. */
	public static BlockHitResult hitResult(BlockPos pos, Direction face) {
		Vec3 centre = Vec3.atCenterOf(pos);
		Vec3 hit = centre.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		return new BlockHitResult(hit, face, pos, false);
	}

	/** Hit result aimed at the top face of a block, which is what you place crystals on. */
	public static BlockHitResult topFace(BlockPos pos) {
		Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		return new BlockHitResult(hit, Direction.UP, pos, false);
	}

	/** Crystal entities near a position. */
	public static List<Entity> nearbyCrystals(Level level, Player player, Vec3 centre, double range) {
		AABB box = new AABB(centre.x - range, centre.y - range, centre.z - range,
				centre.x + range, centre.y + range, centre.z + range);
		List<Entity> result = new ArrayList<>();
		for (Entity entity : level.getEntities(player, box, e -> e instanceof EndCrystal)) {
			result.add(entity);
		}
		return result;
	}

	public static boolean isUsingRightClick(Minecraft client) {
		return client.options != null && client.options.keyUse.isDown();
	}
}
