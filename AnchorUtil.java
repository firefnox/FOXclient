package com.foxclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Respawn-anchor helpers, shared by the anchor macro and its HUD diagnostics.
 *
 * <p>1.4.0's macro searched for a free cell around the target and used the first one it found —
 * which is usually the block the target is <em>standing in</em>. Vanilla rejects block placement
 * against an entity, so no anchor ever appeared and the state machine spun between PLACE and
 * CHARGE forever. Two rules fix that: a cell must not intersect any entity's bounding box, and the
 * click has to land on the real supporting block, not on whatever happens to be below the cell.</p>
 */
public final class AnchorUtil {

	/** A cell an anchor can go in, the solid block to click, and the face of that block to click. */
	public record Placement(BlockPos cell, BlockPos support, Direction face) {
	}

	private static final Direction[] SUPPORT_ORDER = {
			Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
	};

	private AnchorUtil() {
	}

	/** Charge level 0…4, or -1 when the block is not a respawn anchor. */
	public static int charge(Level level, BlockPos pos) {
		if (level == null || pos == null) {
			return -1;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
			return -1;
		}
		return state.getValue(RespawnAnchorBlock.CHARGE);
	}

	public static boolean isAnchor(Level level, BlockPos pos) {
		return pos != null && level != null && level.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR;
	}

	/**
	 * Finds the best cell for an anchor near {@code centre}.
	 *
	 * <p>Candidate cells must be air, within reach, not inside any entity, and must have a solid
	 * neighbour to click. Ground-supported cells win, then the closest to the target.</p>
	 */
	public static Placement findCell(Level level, Player player, Vec3 centre, int radius) {
		BlockPos origin = BlockPos.containing(centre);
		Placement best = null;
		double bestScore = Double.MAX_VALUE;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (!level.getBlockState(candidate).isAir()) {
						continue;
					}
					if (!CombatUtil.withinReach(player, candidate)) {
						continue;
					}
					if (occupied(level, candidate)) {
						continue;
					}
					Direction supportFace = null;
					BlockPos support = null;
					for (Direction direction : SUPPORT_ORDER) {
						BlockPos neighbour = candidate.relative(direction);
						if (level.isOutsideBuildHeight(neighbour)) {
							continue;
						}
						BlockState state = level.getBlockState(neighbour);
						if (state.isAir() || !state.isSolidRender()) {
							continue;
						}
						support = neighbour;
						supportFace = direction.getOpposite();
						break;
					}
					if (support == null) {
						continue;
					}

					double distance = centre.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5,
							candidate.getZ() + 0.5);
					// Ground-supported cells are much better: the anchor is visible and clickable.
					double score = supportFace == Direction.UP ? distance : distance + 100.0;
					if (score < bestScore) {
						bestScore = score;
						best = new Placement(candidate, support, supportFace);
					}
				}
			}
		}
		return best;
	}

	/** True when a block-sized box at {@code pos} would overlap a living entity. */
	private static boolean occupied(Level level, BlockPos pos) {
		AABB box = new AABB(pos).deflate(0.02);
		return !level.getEntities((Entity) null, box, Entity::isAlive).isEmpty();
	}

	/**
	 * A hit result aimed at the middle of the support block's face that touches the cell.
	 * This is what {@code useItemOn} needs in order to place into the right gap.
	 */
	public static BlockHitResult hitBetween(BlockPos support, Direction face) {
		Vec3 centre = Vec3.atCenterOf(support);
		Vec3 hit = centre.add(face.getStepX() * 0.49, face.getStepY() * 0.49, face.getStepZ() * 0.49);
		return new BlockHitResult(hit, face, support, false);
	}

	/** Hit result on the anchor itself, used for charging and detonating. */
	public static BlockHitResult hitAnchor(BlockPos anchor) {
		Vec3 hit = Vec3.atCenterOf(anchor).add(0, 0.35, 0);
		return new BlockHitResult(hit, Direction.UP, anchor, false);
	}
}
