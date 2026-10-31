package com.mallowwww.bringbacknightmares.mixin;

import com.mallowwww.bringbacknightmares.Constants;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
                    var testZombie = new Zombie(level);
                    level.addFreshEntity(testZombie);
                    var checkPos = pos.offset(x, y, z);
                    if (
                            level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty()
                            && level.getBlockState(checkPos.above()).getCollisionShape(level, checkPos.above()).isEmpty()
                            && level.getBlockState(checkPos.below()).entityCanStandOn(
                                    level, checkPos.below(), testZombie
                            )
                            && Zombie.isDarkEnoughToSpawn(level, checkPos, random)) {
                        // Check if a mob from this position can path to the player
                        testZombie.teleportTo(checkPos.getX() + .5f, checkPos.getY(), checkPos.getZ() + .5f);
                        Set<Direction> checkDirections = Set.of(
                                Direction.EAST,
                                Direction.WEST,
                                Direction.NORTH,
                                Direction.SOUTH
                        );
                        boolean flag = false;
                        for (var d : checkDirections) {
                            var path = testZombie.getNavigation().isStableDestination(pos.relative(d));

                            if (path)
                                flag = true;
                        }
                        if (!flag) {
                            testZombie.remove(Entity.RemovalReason.DISCARDED);
                            continue;
                        }
                        // do the funny
                        testZombie.teleportTo(pos.getX() + .5f, pos.getY() + .8f, pos.getZ() + .5f);
                        player.stopSleepInBed(true, true);
                        testZombie.setTarget(player);
                        fail.set(true);
                        return false;

                    } else {
                        testZombie.remove(Entity.RemovalReason.DISCARDED);
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
}
