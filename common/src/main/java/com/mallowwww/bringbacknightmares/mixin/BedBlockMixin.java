package com.mallowwww.bringbacknightmares.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.minecraft.world.level.block.BedBlock.OCCUPIED;
import static net.minecraft.world.level.block.BedBlock.PART;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin extends HorizontalDirectionalBlock {
    @Shadow
    public static boolean canSetSpawn(Level p_49489_) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    private BedBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    @Overwrite
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BedPart part = state.getValue(PART);
        BlockState otherState = level.getBlockState(pos.relative(bringbacknightmares$getNeighbourDirection(part, state.getValue(FACING))));
        if (otherState.is(this) && otherState.getValue(PART) != part) {
            if (part != BedPart.HEAD) {
                pos = pos.relative(state.getValue(FACING));
                state = level.getBlockState(pos);
            }

//            BedRule bedRule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            if (!canSetSpawn(level)) {
                level.removeBlock(pos, false);
                BlockPos blockpos = pos.relative(((Direction)state.getValue(FACING)).getOpposite());
                if (level.getBlockState(blockpos).is(this)) {
                    level.removeBlock(blockpos, false);
                }

                Vec3 vec3 = pos.getCenter();
                level.explode((Entity)null, level.damageSources().badRespawnPointExplosion(vec3), (ExplosionDamageCalculator)null, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS;
            } else if (state.getValue(OCCUPIED)) {
                if (!this.bringbacknightmares$kickVillagerOutOfBed(level, pos)) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
                }

                return InteractionResult.SUCCESS;
            } else {
                final BlockPos passedPos = new BlockPos(pos);
                player.startSleepInBed(pos).ifLeft((problem) -> {
                    if (problem == Player.BedSleepingProblem.NOT_SAFE) {
                        player.startSleeping(passedPos);
                    }
                    if (problem.getMessage() != null) {
                        player.displayClientMessage(problem.getMessage(), true);
                    }

                });
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.CONSUME;
        }
    }
    @Unique
    private static Direction bringbacknightmares$getNeighbourDirection(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }
    @Unique
    private boolean bringbacknightmares$kickVillagerOutOfBed(Level level, BlockPos pos) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        if (villagers.isEmpty()) {
            return false;
        } else {
            ((Villager)villagers.get(0)).stopSleeping();
            return true;
        }
    }
}