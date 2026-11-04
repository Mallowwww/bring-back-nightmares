package com.mallowwww.bringbacknightmares.config;

import com.teamresourceful.resourcefulconfig.api.annotations.Config;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;

@Config(value = "nightmares")
public class ModConfig {
    public enum AllowedMob {
        ZOMBIE, SKELETON, SPIDER;

        @Override
        public String toString() {
            var old = super.toString();
            return old.charAt(0) + old.substring(1).toLowerCase();
        }
    }
    @ConfigEntry(
            id = "allowedMobs",
            translation = "config.bringbacknightmares.allowedmobs"
    )
    @ConfigOption.Select
    public static AllowedMob[] ALLOWED_MOBS = new AllowedMob[] {
            AllowedMob.ZOMBIE,
            AllowedMob.SKELETON,
            AllowedMob.SPIDER
    };
}