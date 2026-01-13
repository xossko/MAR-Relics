package com.vladisss.marrelics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vladisss.marrelics.MARRelicsMod;
import com.vladisss.marrelics.client.ManaShieldClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = MARRelicsMod.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class ManaShieldRenderHandler {

    private static final ResourceLocation TEX =
            new ResourceLocation(MARRelicsMod.MODID, "textures/misc/mana_shield.png");

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        var player = event.getEntity();
        if (!ManaShieldClientState.isEnabled(player.getUUID())) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();

// на сколько выше головы (в блоках)
        double extraAbove = 0.25;
        double h = player.getBbHeight() + extraAbove;
        float sy = (float)(h / 2.0f);

// было узко -> делаем шире
        float sxz = sy * 0.85f;   // попробуй 0.80..0.95

        pose.translate(0.0, sy, 0.0);
        pose.scale(sxz, sy, sxz);



        float pt = Minecraft.getInstance().getFrameTime();
        float t = (player.tickCount + pt) * 0.05f;

        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEX));

        // Слой 1: основные полосы
        renderSphere(pose, vc, 1.0f, 18, 28, t,
                0.18f, 0.55f, 1.00f, 0.30f,
                0.35f, // tilt
                12.0f, // waveFreq
                0.035f,// waveAmp
                0.12f  // scrollSpeed
        );

        // Слой 2: “перелив” поверх
        renderSphere(pose, vc, 1.01f, 18, 28, t + 10.0f,
                0.55f, 0.85f, 1.00f, 0.18f,
                -0.20f,
                9.0f,
                0.025f,
                -0.18f
        );

        pose.popPose();
    }

    private static void renderSphere(PoseStack pose, VertexConsumer vc,

                                     float r, int stacks, int slices,
                                     float t,
                                     float baseR, float baseG, float baseB, float baseA,
                                     float tilt,
                                     float waveFreq,
                                     float waveAmp,
                                     float scrollSpeed) {

        var mat = pose.last().pose();
        var nmat = pose.last().normal();

        for (int i = 0; i < stacks; i++) {
            float v0 = (float) i / stacks;
            float v1 = (float) (i + 1) / stacks;

            double phi0 = Math.PI * (v0 - 0.5);
            double phi1 = Math.PI * (v1 - 0.5);

            float y0 = (float) (Math.sin(phi0) * r);
            float y1 = (float) (Math.sin(phi1) * r);

            float rr0 = (float) (Math.cos(phi0) * r);
            float rr1 = (float) (Math.cos(phi1) * r);

            for (int j = 0; j < slices; j++) {
                float u0 = (float) j / slices;
                float u1 = (float) (j + 1) / slices;

                double th0 = 2.0 * Math.PI * u0;
                double th1 = 2.0 * Math.PI * u1;

                float x00 = (float) (Math.cos(th0) * rr0);
                float z00 = (float) (Math.sin(th0) * rr0);

                float x01 = (float) (Math.cos(th1) * rr0);
                float z01 = (float) (Math.sin(th1) * rr0);

                float x10 = (float) (Math.cos(th0) * rr1);
                float z10 = (float) (Math.sin(th0) * rr1);

                float x11 = (float) (Math.cos(th1) * rr1);
                float z11 = (float) (Math.sin(th1) * rr1);

                // Искажаем UV: наклон + волна + прокрутка
                float uu00 = warpU(u0, v0, t, tilt, waveFreq, waveAmp, scrollSpeed);
                float uu01 = warpU(u1, v0, t, tilt, waveFreq, waveAmp, scrollSpeed);
                float uu10 = warpU(u0, v1, t, tilt, waveFreq, waveAmp, scrollSpeed);
                float uu11 = warpU(u1, v1, t, tilt, waveFreq, waveAmp, scrollSpeed);

                float vv00 = warpV(u0, v0, t);
                float vv01 = warpV(u1, v0, t);
                float vv10 = warpV(u0, v1, t);
                float vv11 = warpV(u1, v1, t);

                // Перелив альфы/цвета (легкий “shimmer”)
                float shimmer = 0.80f + 0.20f * Mth.sin(t * 1.4f + (u0 + v0) * 10.0f);
                float a = baseA * shimmer;
                float mask00 = dnaMask(u0, v0, t);
                float mask10 = dnaMask(u0, v1, t);
                float mask11 = dnaMask(u1, v1, t);
                float mask01 = dnaMask(u1, v0, t);

// Базовая “плёнка” пузыря (всегда есть чуть-чуть)
                float baseFilm = 0.12f;

// Нити ДНК добавляют яркость/альфу
                float a00 = baseA * (baseFilm + 0.88f * mask00);
                float a10 = baseA * (baseFilm + 0.88f * mask10);
                float a11 = baseA * (baseFilm + 0.88f * mask11);
                float a01 = baseA * (baseFilm + 0.88f * mask01);

// Небольшой “перелив” по цвету на нитях
                float r00 = baseR + 0.20f * mask00;
                float g00 = baseG + 0.25f * mask00;
                float b00 = baseB + 0.35f * mask00;

// Дальше put(...) как обычно, только с персональными r/g/b/a
                put(vc, mat, nmat, x00, y0, z00, u0, v0, r00, g00, b00, a00);
                put(vc, mat, nmat, x00, y0, z00, uu00, vv00, baseR, baseG, baseB, a);
                put(vc, mat, nmat, x10, y1, z10, uu10, vv10, baseR, baseG, baseB, a);
                put(vc, mat, nmat, x11, y1, z11, uu11, vv11, baseR, baseG, baseB, a);

                put(vc, mat, nmat, x00, y0, z00, uu00, vv00, baseR, baseG, baseB, a);
                put(vc, mat, nmat, x11, y1, z11, uu11, vv11, baseR, baseG, baseB, a);
                put(vc, mat, nmat, x01, y0, z01, uu01, vv01, baseR, baseG, baseB, a);
            }
        }
    }

    private static float warpU(float u, float v, float t,
                               float tilt, float waveFreq, float waveAmp, float scrollSpeed) {
        // tilt делает диагональные полосы, waveAmp/waveFreq — “завивку”, scrollSpeed — движение
        return u + v * tilt + (t * scrollSpeed) + waveAmp * Mth.sin(v * waveFreq + t * 1.2f);
    }

    private static float warpV(float u, float v, float t) {
        // чуть “дрожи” по V, чтобы не было слишком геометрично
        return v + 0.015f * Mth.sin(u * 10.0f + t * 0.9f);
    }

    private static void put(VertexConsumer vc,
                            org.joml.Matrix4f mat,
                            org.joml.Matrix3f nmat,
                            float x, float y, float z,
                            float u, float v,
                            float r, float g, float b, float a) {
        vc.vertex(mat, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0x00F000F0)
                .normal(nmat, x, y, z)
                .endVertex();
    }
    private static float fract(float x) {
        return x - (float)Math.floor(x);
    }

    private static float wrapDist01(float a, float b) {
        // расстояние на окружности [0..1)
        float d = Math.abs(a - b);
        return Math.min(d, 1.0f - d);
    }

    private static float smoothPulse(float d, float width) {
        // 1 в центре полосы, к 0 уходит плавно
        // width ~ 0.02..0.08
        float x = d / width;
        // экспонента даёт “мыльный” мягкий край
        return (float)Math.exp(-(x * x) * 4.0f);
    }

    /** Две нити ДНК (0..1), где 1 = максимум яркости. */
    private static float dnaMask(float u, float v, float time) {
        float turns = 2.4f;      // сколько витков по высоте пузыря (2.0..3.5)
        float speed = 0.015f;    // скорость “ползания” (медленно как пузырь)
        float width = 0.045f;    // толщина нитей

        // небольшая “живая” неровность, чтобы не было слишком математически ровно
        float wobble = 0.03f * net.minecraft.util.Mth.sin(v * 10.0f + time * 0.7f);

        float centerU1 = fract(v * turns + time * speed + wobble);
        float centerU2 = fract(centerU1 + 0.5f);

        float d1 = wrapDist01(u, centerU1);
        float d2 = wrapDist01(u, centerU2);

        float strand1 = smoothPulse(d1, width);
        float strand2 = smoothPulse(d2, width);

        // чуть “дышащая” интенсивность
        float breathe = 0.85f + 0.15f * net.minecraft.util.Mth.sin(time * 0.25f + v * 3.0f);

        return Math.min(1.0f, (strand1 + strand2) * breathe);
    }

}
