package com.power.morediodes.components.functions;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Translation helpers: every text this mod shows goes through a key in
 * assets/morediodes/lang/*.json, never a hard-coded string.
 *
 * Goggle tooltips are built on the client, so the key is resolved there with the player's language.
 * Messages sent from the server (action bar) use {@link #component} so the CLIENT translates them.
 */
public final class ModLang {
    private ModLang() {
    }

    /** A goggle tooltip line (add .style(...) and .forGoggles(tooltip)) from a translation key. */
    public static LangBuilder text(String key, Object... args) {
        return Lang.text(Component.translatable(key, args).getString());
    }

    /** A translatable component, translated on the receiving client (use for messages sent by the server). */
    public static MutableComponent component(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** The translated text as a plain string, for building longer lists. */
    public static String string(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Client side only (goggle tooltips): is the local player in creative mode? Used to show the
     * creative-only repair hint to creative players and hide it in survival.
     */
    public static boolean localPlayerIsCreative() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }
}
