package com.mallowwww.bringbacknightmares.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.villager.Villager;
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
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.minecraft.world.level.block.BedBlock.OCCUPIED;
import static net.minecraft.world.level.block.BedBlock.PART;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin extends HorizontalDirectionalBlock {
    private BedBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS_SERVER;
        BedPart part = state.getValue(PART);
        BlockState otherState = level.getBlockState(pos.relative(bringbacknightmares$getNeighbourDirection(part, state.getValue(FACING))));
        if (otherState.is(this) && otherState.getValue(PART) != part) {
            if (part != BedPart.HEAD) {
                pos = pos.relative(state.getValue(FACING));
                state = level.getBlockState(pos);
            }

            BedRule bedRule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            if (bedRule.explodes()) {
                Optional<Component> message = bedRule.errorMessage();
                Objects.requireNonNull(player);
                message.ifPresent(player::sendOverlayMessage);
                level.removeBlock(pos, false);
                BlockPos blockPos = pos.relative(((Direction)state.getValue(FACING)).getOpposite());
                if (level.getBlockState(blockPos).is(this)) {
                    level.removeBlock(blockPos, false);
                }

                Vec3 boomPos = Vec3.atCenterOf(pos);
                level.explode(null, level.damageSources().badRespawnPointExplosion(boomPos), null, boomPos, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS_SERVER;
            } else if (state.getValue(OCCUPIED)) {
                if (!this.bringbacknightmares$kickVillagerOutOfBed(level, pos)) {
                    player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
                }

                return InteractionResult.SUCCESS_SERVER;
            } else {
                if (!bedRule.canSleep(level)) {
                    player.startSleeping(pos);
                    return InteractionResult.SUCCESS_SERVER;
                }
                player.startSleepInBed(pos).ifLeft((problem) -> {
                    if (problem.message() != null) {
                        player.sendOverlayMessage(problem.message());
                    }

                });
                return InteractionResult.SUCCESS_SERVER;
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