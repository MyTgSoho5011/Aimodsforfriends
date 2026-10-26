package com.sohonet.codeforge.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Список установленных Fabric-модов.
 *
 * Нужен скриптам и туториалу: чтобы импортировать классы чужого мода,
 * надо знать, что этот мод вообще установлен (см. главу Ch5).
 */
public final class ModListHelper {

    public record ModInfo(String id, String name, String version) {
    }

    private ModListHelper() {
    }

    public static List<ModInfo> getInstalledMods() {
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            mods.add(new ModInfo(
                container.getMetadata().getId(),
                container.getMetadata().getName(),
                container.getMetadata().getVersion().getFriendlyString()));
        }
        mods.sort(Comparator.comparing(ModInfo::id, String.CASE_INSENSITIVE_ORDER));
        return mods;
    }
}
