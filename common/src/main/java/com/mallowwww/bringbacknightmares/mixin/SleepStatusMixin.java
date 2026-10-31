package com.mallowwww.bringbacknightmares.mixin;

import com.mallowwww.bringbacknightmares.Constants;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {
    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true)
    private void areEnoughDeepSleeping(int sleepPercentageNeeded, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> ci) {
        ci.cancel();
        AtomicBoolean fail = new AtomicBoolean(false);
        int deepSleepers = (int)players.stream().filter(Player::isSleepingLongEnough).filter(player -> {
            // Check if the player is sleeping in an unsafe spot, and if so, kick them out of bed and spawn a mob in front of them
            // Also filter them from this list so they're not part of the calculation
            var level = player.level();
            var random = level.getRandom();
            var pos = player.blockPosition();
            var radius = 20; // Range the mob will try to spawn in; means that a large well-lit area can be considered safe
            for (int i = 0; i < 20; i++) { // 20 times for parity with beta; means there's a chance it misses a spot. Adds some grace.
                int x = (int) (random.nextDouble() * radius * 2 - radius);
                int z = (int) (random.nextDouble() * radius * 2 - radius);
                for (int y = -radius; y < radius; y++) {
                    // Can a mob spawn here?
                    PathfinderMob spawnedMob = makeRandomMob(level);
                    level.addFreshEntity(spawnedMob);
                    var checkPos = pos.offset(x, y, z);
                    if (
                            level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty()
                            && level.getBlockState(checkPos.above()).getCollisionShape(level, checkPos.above()).isEmpty()
                            && level.getBlockState(checkPos.below()).entityCanStandOn(
                                    level, checkPos.below(), spawnedMob
                            )
                            && Zombie.isDarkEnoughToSpawn(level, checkPos, random)) {
                        // Check if a mob from this position can path to the player
                        spawnedMob.teleportTo(checkPos.getX() + .5f, checkPos.getY(), checkPos.getZ() + .5f);
                        Set<Direction> checkDirections = Set.of(
                                Direction.EAST,
                                Direction.WEST,
                                Direction.NORTH,
                                Direction.SOUTH
                        );
                        boolean flag = false;
                        for (var d : checkDirections) {
                            var path = spawnedMob.getNavigation().isStableDestination(pos.relative(d));

                            if (path)
                                flag = true;
                        }
                        if (!flag) {
                            spawnedMob.remove(Entity.RemovalReason.DISCARDED);
                            continue;
                        }
                        // do the funny
                        spawnedMob.teleportTo(pos.getX() + .5f, pos.getY() + .8f, pos.getZ() + .5f);
                        player.stopSleepInBed(true, true);
                        spawnedMob.setTarget(player);
                        fail.set(true);
                        return false;

                    } else {
                        spawnedMob.remove(Entity.RemovalReason.DISCARDED);
                    }
                }
            }
            return true;
        }).count();
        if (fail.get()) ci.setReturnValue(false);
        else ci.setReturnValue(deepSleepers >= this.sleepersNeeded(sleepPercentageNeeded));
    }
    @Shadow
    public abstract int sleepersNeeded(int sleepPercentageNeeded);
    @Unique
    private static PathfinderMob makeRandomMob(Level level) {
        var random = level.getRandom();
        PathfinderMob entity;
        float val = random.nextFloat();
        if (val > .6f)
            entity = new Zombie(EntityTypes.ZOMBIE, level);
        else if (val > .2f)
            entity = new Skeleton(EntityTypes.SKELETON, level);
        else
            entity = new Spider(EntityTypes.SPIDER, level);
        return entity;
    }
}
