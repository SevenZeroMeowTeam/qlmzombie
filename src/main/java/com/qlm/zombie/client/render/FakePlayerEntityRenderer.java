package com.qlm.zombie.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerEntityRenderer extends HumanoidMobRenderer<FakePlayerEntity, PlayerModel<FakePlayerEntity>> {

    private final PlayerModel<FakePlayerEntity> defaultModel;
    private final PlayerModel<FakePlayerEntity> slimModel;
    private static final Map<String, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> RATE_LIMIT = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_MS = 5000;

    public FakePlayerEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);

        this.defaultModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()
        ));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(FakePlayerEntity entity) {
        String skinURL = entity.getSkinURL();

        if (skinURL != null && !skinURL.isEmpty()) {
            return resolveCustomSkinTexture(skinURL, entity);
        }

        UUID uuid = entity.getPlayerUUID().orElse(null);
        if (uuid == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        GameProfile profile = entity.getGameProfile();
        Minecraft minecraft = Minecraft.getInstance();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> skinMap =
                minecraft.getSkinManager().getInsecureSkinInformation(profile);

        if (skinMap.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            MinecraftProfileTexture texture = skinMap.get(MinecraftProfileTexture.Type.SKIN);
            return minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
        }

        return DefaultPlayerSkin.getDefaultSkin();
    }

    private ResourceLocation resolveCustomSkinTexture(String skinURL, FakePlayerEntity entity) {
        ResourceLocation cached = SKIN_CACHE.get(skinURL);
        if (cached != null) return cached;

        if (skinURL.startsWith("data:image/png;base64,")) {
            try {
                String base64Data = skinURL.substring("data:image/png;base64,".length());
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                ResourceLocation skin = loadSkinFromBytes(imageBytes, entity);
                SKIN_CACHE.put(skinURL, skin);
                return skin;
            } catch (Exception e) {
                return DefaultPlayerSkin.getDefaultSkin();
            }
        }

        if (skinURL.startsWith("http://") || skinURL.startsWith("https://")) {
            fetchSkinFromURLAsync(skinURL, entity);
            return DefaultPlayerSkin.getDefaultSkin();
        }

        return ResourceLocation.parse(skinURL);
    }

    private ResourceLocation loadSkinFromBytes(byte[] imageBytes, FakePlayerEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image != null) {
                boolean slim = isSlimSkin(image);
                entity.setSlim(slim);
            }
        } catch (IOException ignored) {
        }

        return minecraft.getSkinManager().registerTexture(
                new MinecraftProfileTexture(skinURLFromBytes(imageBytes), null),
                MinecraftProfileTexture.Type.SKIN
        );
    }

    private String skinURLFromBytes(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private void fetchSkinFromURLAsync(String url, FakePlayerEntity entity) {
        long now = System.currentTimeMillis();
        Long lastFetch = RATE_LIMIT.get(url);
        if (lastFetch != null && now - lastFetch < RATE_LIMIT_MS) {
            return;
        }
        RATE_LIMIT.put(url, now);

        CompletableFuture.runAsync(() -> {
            try {
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                try (InputStream is = connection.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] imageBytes = baos.toByteArray();
                    
                    if (imageBytes.length == 0) {
                        com.qlm.zombie.QLMZombieMod.LOGGER.warn("Failed to fetch skin from URL {}: empty response", url);
                        return;
                    }

                    String contentType = connection.getContentType();
                    if (contentType != null && !contentType.contains("image")) {
                        com.qlm.zombie.QLMZombieMod.LOGGER.warn("Failed to fetch skin from URL {}: content type is not image ({}), length: {}", url, contentType, imageBytes.length);
                        return;
                    }

                    String base64URL = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);

                    Minecraft.getInstance().execute(() -> {
                    });
                }
            } catch (java.net.SocketTimeoutException e) {
                com.qlm.zombie.QLMZombieMod.LOGGER.warn("Failed to fetch skin from URL {}: timeout", url);
            } catch (java.io.IOException e) {
                com.qlm.zombie.QLMZombieMod.LOGGER.warn("Failed to fetch skin from URL {}: {}", url, e.getMessage());
            } catch (Exception e) {
            }
        });
    }

    private boolean isSlimSkin(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width >= 64 && height >= 64) {
            BufferedImage armRegion = image.getSubimage(50, 16, 2, 4);
            for (int x = 0; x < armRegion.getWidth(); x++) {
                for (int y = 0; y < armRegion.getHeight(); y++) {
                    int pixel = armRegion.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xFF;
                    if (alpha != 0) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected void scale(FakePlayerEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public Vec3 getRenderOffset(FakePlayerEntity entity, float partialTick) {
        return entity.isCrouching() ? new Vec3(0.0, -0.125, 0.0) : super.getRenderOffset(entity, partialTick);
    }
}