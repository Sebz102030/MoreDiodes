package com.power.morediodes.components.functions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.power.morediodes.MoreDiodes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Client resource reload listener: reads every {@code assets/<namespace>/glow/*.json} (the highest resource
 * pack wins, like any other asset) and hands the layouts to {@link GlowLayout}. Reloaded with F3+T.
 */
public final class GlowLayoutLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();

    private GlowLayoutLoader() {
        super(GSON, "glow");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, GlowLayout> layouts = new HashMap<>();
        files.forEach((id, json) -> {
            if (!id.getNamespace().equals(MoreDiodes.MODID))
                return;
            try {
                layouts.put(id, GlowLayout.parse(json.getAsJsonObject()));
            } catch (RuntimeException e) {
                MoreDiodes.LOGGER.error("Could not read glow layout {}: {}", id, e.toString());
            }
        });
        GlowLayout.replaceAll(layouts);
    }

    @EventBusSubscriber(modid = MoreDiodes.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new GlowLayoutLoader());
        }
    }
}
