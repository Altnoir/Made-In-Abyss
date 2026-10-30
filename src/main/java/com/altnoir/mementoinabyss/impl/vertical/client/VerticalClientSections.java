package com.altnoir.mementoinabyss.impl.vertical.client;

import com.altnoir.mementoinabyss.compat.iris.IrisRenderCompat;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLink;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLinks;
import com.altnoir.mementoinabyss.impl.vertical.VerticalRaytrace;
import com.altnoir.mementoinabyss.impl.vertical.network.VerticalSectionPayload;
import com.altnoir.mementoinabyss.impl.vertical.network.VerticalSectionReceiptPayload;
import com.altnoir.mementoinabyss.infrastructure.worldgen.dimension.MiaDimensions;
import com.altnoir.mementoinabyss.infrastructure.worldgen.dimension.VerticalSectionRange;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

public final class VerticalClientSections {
    private static final Map<Key, VerticalSectionPayload> DATA = new LinkedHashMap<>();
    private static long boundaryPredictionStarted;
    private static int receiptTicks;

    public static void accept(VerticalSectionPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null || !payload.viewer().equals(level.dimension())) return;
        Key key = new Key(payload.source(), payload.chunkX(), payload.sectionY(), payload.chunkZ());
        var previous = DATA.get(key);
        if (previous != null && previous.revision() >= payload.revision()) return;
        if (previous == null && DATA.size() >= 192) return;
        DATA.put(key, payload);
        VerticalResidentWorlds.accept(payload);
        ClientPacketDistributor.sendToServer(
                new VerticalSectionReceiptPayload(
                        payload.source(),
                        payload.chunkX(),
                        payload.sectionY(),
                        payload.chunkZ(),
                        payload.revision()));
    }

    public static @Nullable RemoteHit pick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || IrisRenderCompat.isShaderPackInUse())
            return null;
        double reach =
                Math.min(16, mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));
        for (var link : CrossDimensionLodLinks.forTargets(mc.level)) {
            var anchor =
                    DATA.values().stream()
                            .filter(
                                    p ->
                                            p.viewer().equals(mc.level.dimension())
                                                    && p.source().equals(link.source()))
                            .findFirst();
            if (anchor.isEmpty()) continue;
            var hit =
                    VerticalRaytrace.trace(
                            mc.level,
                            new VerticalSectionView(DATA, anchor.get()),
                            link,
                            mc.player,
                            reach);
            if (hit == null) continue;
            var pos = hit.getBlockPos();
            if (DATA.containsKey(
                    new Key(
                            link.source(),
                            Math.floorDiv(pos.getX(), 16),
                            Math.floorDiv(pos.getY(), 16),
                            Math.floorDiv(pos.getZ(), 16))))
                return new RemoteHit(link.source(), hit);
        }
        return null;
    }

    public static @Nullable CollisionGetter collisionView(Level level, CrossDimensionLodLink link) {
        for (var p : DATA.values())
            if (p.viewer().equals(level.dimension()) && p.source().equals(link.source()))
                return new VerticalSectionView(DATA, p);
        return null;
    }

    public record RemoteHit(ResourceKey<Level> source, BlockHitResult hit) {}

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (++receiptTicks % 20 == 0)
            for (var p : DATA.values()) {
                if (p.viewer().equals(mc.level.dimension()))
                    ClientPacketDistributor.sendToServer(
                            new VerticalSectionReceiptPayload(
                                    p.source(),
                                    p.chunkX(),
                                    p.sectionY(),
                                    p.chunkZ(),
                                    p.revision()));
            }
        var iterator = DATA.entrySet().iterator();
        var window = VerticalSectionRange.around(mc.player.getY());
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var p = entry.getValue();
            if ((!p.viewer().equals(mc.level.dimension()))
                    || Math.abs(p.chunkX() - mc.player.chunkPosition().x()) > 1
                    || Math.abs(p.chunkZ() - mc.player.chunkPosition().z()) > 1
                    || (p.sectionY() * 16 + p.offset() >= window.maxYExclusive()
                            || p.sectionY() * 16 + p.offset() + 16 <= window.minY())) {
                iterator.remove();
            }
        }
    }

    public static void holdAtPendingBoundary() {
        var mc = Minecraft.getInstance();
        if (mc.level == null
                || mc.player == null
                || !MiaDimensions.isMiaDimension(mc.level.dimension())) return;
        double y = mc.player.getY();
        boolean below = y < mc.level.getMinY();
        boolean above = y >= mc.level.getMaxY() + 1;
        if (!below && !above) {
            boundaryPredictionStarted = 0;
            return;
        }
        var connection =
                CrossDimensionLodLinks.forTargets(mc.level).stream()
                        .filter(
                                link ->
                                        MiaDimensions.isMiaDimension(link.source())
                                                && (link.displayYOffset() < 0) == below)
                        .findFirst();
        if (connection.isEmpty()) return;
        long now = System.nanoTime();
        if (boundaryPredictionStarted == 0) boundaryPredictionStarted = now;
        double overshoot = below ? mc.level.getMinY() - y : y - mc.level.getMaxY() - 1;
        if (!mc.player.isPassenger()
                && !IrisRenderCompat.isShaderPackInUse()
                && now - boundaryPredictionStarted < 2_000_000_000L
                && overshoot < 96
                && readyToCross(connection.get())) return;
        mc.player.setPos(
                mc.player.getX(),
                below
                        ? mc.level.getMinY() + .05
                        : mc.level.getMaxY() + 1 - mc.player.getBbHeight() - .05,
                mc.player.getZ());
        var motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, 0, motion.z);
    }

    private static boolean readyToCross(CrossDimensionLodLink link) {
        var mc = Minecraft.getInstance();
        var box = mc.player.getBoundingBox().inflate(1).move(0, -link.displayYOffset(), 0);
        int minY = Math.max(link.sourceHeight().minY(), (int) Math.floor(box.minY));
        int maxY =
                Math.min(
                        link.sourceHeight().minY() + link.sourceHeight().height() - 1,
                        (int) Math.floor(box.maxY));
        if (minY > maxY) return false;
        for (int x = (int) Math.floor(box.minX / 16); x <= (int) Math.floor(box.maxX / 16); x++)
            for (int z = (int) Math.floor(box.minZ / 16); z <= (int) Math.floor(box.maxZ / 16); z++)
                for (int y = Math.floorDiv(minY, 16); y <= Math.floorDiv(maxY, 16); y++) {
                    Key key = new Key(link.source(), x, y, z);
                    var payload = DATA.get(key);
                    if (payload == null || !payload.viewer().equals(mc.level.dimension()))
                        return false;
                }
        return true;
    }

    public static void hydrate(ClientLevel level) {
        int centerX = Minecraft.getInstance().player.chunkPosition().x();
        int centerZ = Minecraft.getInstance().player.chunkPosition().z();
        level.getChunkSource().updateViewCenter(centerX, centerZ);
        for (var p : DATA.values()) if (p.source().equals(level.dimension())) install(level, p);
    }

    @SuppressWarnings("unchecked")
    public static void install(ClientLevel level, VerticalSectionPayload p) {
        if (!p.source().equals(level.dimension())
                || p.minY() != level.getMinY()
                || p.height() != level.getHeight()) return;
        LevelChunk chunk =
                level.getChunkSource().getChunk(p.chunkX(), p.chunkZ(), ChunkStatus.FULL, false);
        boolean newChunk = chunk == null;
        if (newChunk) chunk = new LevelChunk(level, new ChunkPos(p.chunkX(), p.chunkZ()));
        var section = chunk.getSection(p.sectionY() - level.getMinSectionY());
        var biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (int i = 0; i < 64; i++) {
            var biome = registry.byId(p.biomes()[i]);
            if (biome != null)
                biomes.set(i & 3, (i >> 4) & 3, (i >> 2) & 3, registry.wrapAsHolder(biome));
        }
        BlockState[] palette = new BlockState[p.palette().length];
        for (int i = 0; i < palette.length; i++) palette[i] = Block.stateById(p.palette()[i]);
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    section.setBlockState(
                            x,
                            y,
                            z,
                            palette[Short.toUnsignedInt(p.cells()[(y << 8) | (z << 4) | x])]);
        if (newChunk) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                for (var s : chunk.getSections()) s.write(buffer);
                level.getChunkSource()
                        .replaceWithPacketData(
                                p.chunkX(), p.chunkZ(), buffer, Map.of(), output -> {});
            } finally {
                buffer.release();
            }
        } else {
            level.getChunkSource()
                    .onSectionEmptinessChanged(
                            p.chunkX(), p.sectionY(), p.chunkZ(), section.hasOnlyAir());
            level.onChunkLoaded(chunk.getPos());
        }
        byte[] skyLight = new byte[2048];
        byte[] blockLight = new byte[2048];
        for (int i = 0; i < 4096; i++) {
            int value = Byte.toUnsignedInt(p.light()[i]);
            int shift = (i & 1) * 4;
            skyLight[i >> 1] |= (byte) ((value >>> 4) << shift);
            blockLight[i >> 1] |= (byte) ((value & 15) << shift);
        }
        for (LightLayer layer : LightLayer.values()) {
            level.getLightEngine()
                    .queueSectionData(
                            layer,
                            SectionPos.of(p.chunkX(), p.sectionY(), p.chunkZ()),
                            new DataLayer(layer == LightLayer.SKY ? skyLight : blockLight));
        }
        level.getLightEngine().setLightEnabled(chunk.getPos(), true);
        level.getLightEngine()
                .updateSectionStatus(
                        SectionPos.of(p.chunkX(), p.sectionY(), p.chunkZ()), section.hasOnlyAir());
        VerticalResidentWorlds.tracked(p);
    }

    public static void clear() {
        DATA.clear();
        receiptTicks = 0;
        boundaryPredictionStarted = 0;
    }

    record Key(ResourceKey<Level> source, int x, int y, int z) {}

    private VerticalClientSections() {}
}
