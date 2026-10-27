package com.power.morediodes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.power.morediodes.MoreDiodes;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets an item be drawn differently on a Create belt than in an item frame. Both ask the item model for the same
 * "fixed" display transform, so a model file cannot tell them apart. Here the model is wrapped: while Create's
 * BeltRenderer or DepotRenderer (belts and depots) is on the call stack, the values from
 * {@code assets/morediodes/belt_orientation/<item>.json} REPLACE the model's "fixed" display values.
 *
 * <pre>
 *   { "rotation": [0, -90, 0], "translation": [0, 12, -4], "scale": [2, 2, 2] }
 * </pre>
 *
 * Same format and units as a "display" entry in the item model (rotation in degrees, translation in 1/16 block).
 * Every key is optional: a key that is present replaces that part of "fixed" on belts, a key that is left out keeps
 * the model's own "fixed" value. Items without a file are untouched, and item frames always use "fixed".
 * The offset moves the item in the space the belt draws it in, so it follows the belt. Reload with F3+T.
 */
public final class BeltOrientation {
    private static final Map<ResourceLocation, Entry> ENTRIES = new HashMap<>();
    private static boolean logged;

    /** Values from a file; a null part is not replaced. */
    private record Entry(@Nullable Vector3f rotation, @Nullable Vector3f translation, @Nullable Vector3f scale) {
    }

    private BeltOrientation() {
    }

    /** Create renderers whose items use the belt values: belts and depots. */
    private static final String[] BELT_LIKE_RENDERERS = { "BeltRenderer", "DepotRenderer" };

    /** True if Create's belt or depot renderer is drawing the item right now. */
    private static boolean isBeltRender() {
        return StackWalker.getInstance().walk(frames -> frames.limit(80).anyMatch(f -> {
            String name = f.getClassName();
            for (String renderer : BELT_LIKE_RENDERERS) {
                if (name.contains(renderer))
                    return true;
            }
            return false;
        }));
    }

    /** Reads a component of an ItemTransform (works whether it is a record or has public fields). */
    private static Vector3f read(ItemTransform transform, String name) {
        try {
            return new Vector3f((Vector3f) ItemTransform.class.getMethod(name).invoke(transform));
        } catch (ReflectiveOperationException ignored) {
            try {
                return new Vector3f((Vector3f) ItemTransform.class.getField(name).get(transform));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read ItemTransform." + name, e);
            }
        }
    }

    private static final class Wrapper extends BakedModelWrapper<BakedModel> {
        private final ResourceLocation item;

        Wrapper(BakedModel original, ResourceLocation item) {
            super(original);
            this.item = item;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            if (context == ItemDisplayContext.FIXED) {
                Entry entry = ENTRIES.get(item);
                if (entry != null && isBeltRender()) {
                    if (!logged) {
                        logged = true;
                        MoreDiodes.LOGGER.info("Belt/depot orientation used for {}", item);
                    }
                    ItemTransform fixed = originalModel.getTransforms().getTransform(ItemDisplayContext.FIXED);
                    Vector3f rotation = entry.rotation() != null ? entry.rotation() : read(fixed, "rotation");
                    Vector3f translation = entry.translation() != null
                            ? new Vector3f(entry.translation()).div(16f) : read(fixed, "translation");
                    Vector3f scale = entry.scale() != null ? entry.scale() : read(fixed, "scale");
                    new ItemTransform(rotation, translation, scale).apply(leftHand, poseStack);
                    return originalModel;
                }
            }
            return super.applyTransform(context, poseStack, leftHand);
        }
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new GsonBuilder().create(), "belt_orientation");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                             ProfilerFiller profiler) {
            ENTRIES.clear();
            files.forEach((id, json) -> {
                if (!id.getNamespace().equals(MoreDiodes.MODID))
                    return;
                try {
                    JsonObject o = json.getAsJsonObject();
                    ENTRIES.put(id, new Entry(vec(o, "rotation"), vec(o, "translation"), vec(o, "scale")));
                } catch (RuntimeException ex) {
                    MoreDiodes.LOGGER.error("Could not read belt orientation {}: {}", id, ex.toString());
                }
            });
        }

        @Nullable
        private static Vector3f vec(JsonObject o, String key) {
            if (!o.has(key))
                return null;
            JsonArray a = o.getAsJsonArray(key);
            return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
        }
    }

    @EventBusSubscriber(modid = MoreDiodes.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new Loader());
        }

        @SubscribeEvent
        public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
            Map<ModelResourceLocation, BakedModel> models = event.getModels();
            for (var entry : new ArrayList<>(models.entrySet())) {
                ModelResourceLocation key = entry.getKey();
                if (key.id().getNamespace().equals(MoreDiodes.MODID) && key.variant().equals("inventory"))
                    models.put(key, new Wrapper(entry.getValue(), key.id()));
            }
        }
    }
}
