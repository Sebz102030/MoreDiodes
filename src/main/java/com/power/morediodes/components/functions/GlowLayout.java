package com.power.morediodes.components.functions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.power.morediodes.MoreDiodes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.patryk3211.powergrid.circuits.components.properties.Orientation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Where the lit LEDs of a component are drawn. The positions are NOT in the code: they are read from
 * {@code assets/morediodes/glow/<name>.json}, so a resource pack can move, resize or recolour the glowing
 * parts together with the texture it replaces. See any file in that folder for the format.
 *
 * Positions are in pixels (16 = 1 block) in the component's default orientation, x = east, z = south, from the
 * north-west corner of the footprint; the renderer rotates them with the component. The order of the elements
 * is the segment / diode index the component uses.
 */
public final class GlowLayout {

    public static final ResourceLocation SEVEN_SEGMENT = id("seven_segment");
    public static final ResourceLocation LED_MATRIX = id("led_matrix");
    public static final ResourceLocation LED_MATRIX_ADVANCED = id("led_matrix_advanced");
    public static final ResourceLocation LED_BARGRAPH = id("led_bargraph");

    /** One lit part: a rectangle on the top of the body, in pixels. */
    public record Rect(float x0, float z0, float x1, float z1, float y) {
    }

    private static final Map<ResourceLocation, GlowLayout> LOADED = new HashMap<>();

    private final Rect[] elements;
    private final float halo;
    /** Optional fixed colour from the file (null = the component decides, e.g. the dye colour). */
    private final int[] color;

    private GlowLayout(Rect[] elements, float halo, int[] color) {
        this.elements = elements;
        this.halo = halo;
        this.color = color;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoreDiodes.MODID, path);
    }

    public int size() {
        return elements.length;
    }

    /** The layout loaded for {@code id}, or an empty one (nothing drawn) if the file is missing or broken. */
    public static GlowLayout get(ResourceLocation id) {
        GlowLayout layout = LOADED.get(id);
        return layout != null ? layout : new GlowLayout(new Rect[0], 0, null);
    }

    /** Replaces everything that was loaded (called by the resource reload listener). */
    static void replaceAll(Map<ResourceLocation, GlowLayout> layouts) {
        LOADED.clear();
        LOADED.putAll(layouts);
    }

    /** Parses one layout file. Throws on a malformed file; the loader logs it and skips the file. */
    static GlowLayout parse(JsonObject json) {
        float defaultY = json.has("y") ? json.get("y").getAsFloat() : 2.03f;
        float halo = json.has("halo") ? json.get("halo").getAsFloat() : 0.25f;
        int[] color = null;
        if (json.has("color")) {
            JsonArray c = json.getAsJsonArray("color");
            color = new int[] { c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt() };
        }
        JsonArray list = json.getAsJsonArray("elements");
        Rect[] rects = new Rect[list.size()];
        for (int i = 0; i < rects.length; i++) {
            JsonObject e = list.get(i).getAsJsonObject();
            JsonArray from = e.getAsJsonArray("from");
            JsonArray to = e.getAsJsonArray("to");
            float ax = from.get(0).getAsFloat(), az = from.get(1).getAsFloat();
            float bx = to.get(0).getAsFloat(), bz = to.get(1).getAsFloat();
            JsonElement y = e.get("y");
            rects[i] = new Rect(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz),
                    y != null ? y.getAsFloat() : defaultY);
        }
        return new GlowLayout(rects, halo, color);
    }

    /**
     * Draws the lit elements of a component.
     *
     * @param baseWidth   footprint width in cells in the default orientation
     * @param baseHeight  footprint height in cells in the default orientation
     * @param r,g,b       colour; ignored when the layout file sets its own colour and {@code useFileColor}
     * @param alphaOf     alpha (0..255) of element i, 0 = not lit
     */
    public void render(PoseStack ms, MultiBufferSource bufferSource, Orientation orientation, int baseWidth,
            int baseHeight, boolean useFileColor, int r, int g, int b, IntUnaryOperator alphaOf) {
        if (elements.length == 0)
            return;
        if (useFileColor && color != null) {
            r = color[0];
            g = color[1];
            b = color[2];
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        // Same transform Power Grid applies to the baked model: rotate about the component's origin by
        // orientation * 90 degrees, then shift it back into its (rotated) footprint.
        ms.pushPose();
        switch (orientation) {
            case DOWN -> ms.translate(baseHeight / 16f, 0, 0);
            case LEFT -> ms.translate(baseWidth / 16f, 0, baseHeight / 16f);
            case UP -> ms.translate(0, 0, baseWidth / 16f);
            default -> {
            }
        }
        ms.mulPose(new Quaternionf().rotationY(orientation.ordinal() * (float) Math.PI * 0.5f));
        Matrix4f pose = ms.last().pose();

        float h = halo / 16f;
        for (int i = 0; i < elements.length; i++) {
            int alpha = alphaOf.applyAsInt(i);
            if (alpha <= 0)
                continue;
            Rect e = elements[i];
            float x0 = e.x0() / 16f, x1 = e.x1() / 16f, z0 = e.z0() / 16f, z1 = e.z1() / 16f, y = e.y() / 16f;
            quad(buffer, pose, x0, x1, y, z0, z1, r, g, b, alpha);
            if (h > 0)
                quad(buffer, pose, x0 - h, x1 + h, y - 0.0005f, z0 - h, z1 + h, r * 3 / 4, g * 3 / 4, b * 3 / 4,
                        alpha / 4);
        }
        ms.popPose();
    }

    /** A flat quad facing up, emitted with both windings so it shows regardless of culling. */
    private static void quad(VertexConsumer b, Matrix4f pose, float x0, float x1, float y, float z0, float z1,
            int r, int g, int bl, int a) {
        b.addVertex(pose, x0, y, z0).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z0).setColor(r, g, bl, a);

        b.addVertex(pose, x1, y, z0).setColor(r, g, bl, a);
        b.addVertex(pose, x1, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z1).setColor(r, g, bl, a);
        b.addVertex(pose, x0, y, z0).setColor(r, g, bl, a);
    }
}
