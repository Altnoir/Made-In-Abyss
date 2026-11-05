package com.altnoir.mementoinabyss.compat.dh;

import com.altnoir.mementoinabyss.MementoInAbyss;
import com.altnoir.mementoinabyss.compat.MiaMods;
import com.altnoir.mementoinabyss.compat.iris.IrisRenderCompat;
import com.altnoir.mementoinabyss.impl.lod.client.CrossDimensionLodRenderer;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLink;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLinks;
import com.altnoir.mementoinabyss.impl.vertical.client.VerticalResidentWorlds;
import com.altnoir.mementoinabyss.infrastructure.worldgen.dimension.MiaDimensions;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Optional adapter for the terrain pass and level registry in DH 3.3.3. */
public final class DhCrossDimensionLod {
    private static final String DH = "com.seibel.distanthorizons.";
    private static final boolean REQUESTED =
            Boolean.getBoolean("mia.experimentalDhCrossDimensionLod");
    private static final HashSet<Identifier> REPORTED_LINKS = new HashSet<>();
    private static @Nullable Binding binding;
    private static @Nullable Source source;
    private static @Nullable Frame frame;
    private static boolean composited;
    private static boolean failed;
    private static int ticks;

    public static void tick() {
        if (!REQUESTED || !MiaMods.DISTANTHORIZONS.isLoaded()) return;
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (failed
                || mc.level == null
                || mc.player == null
                || server == null
                || server.isPublished()
                || IrisRenderCompat.isShaderPackInUse()
                || !MementoInAbyss.CONFIGS.graphsSection.crossDimensionLodEnabled.get()) {
            clear();
            return;
        }
        if (++ticks % 5 != 0) return;
        var link =
                CrossDimensionLodLinks.forTargets(mc.level).stream()
                        .filter(DhCrossDimensionLod::supported)
                        .findFirst()
                        .orElse(null);
        var resident = link == null ? null : VerticalResidentWorlds.residentLevel(link.source());
        if (link == null
                || resident == null
                || gap(link, mc.player.getY()) >= CrossDimensionLodLinks.radius(link)) {
            clear();
            return;
        }
        try {
            if (binding == null) binding = new Binding();
            var dh = binding;
            Object world = dh.currentWorld.invoke(null);
            Object configs = dh.configs.get(null);
            if (world == null || configs == null || dh.generatesDistantTerrain(configs)) {
                clear();
                return;
            }
            var models = mc.getModelManager().getBlockStateModelSet();
            if (source == null
                    || source.viewer() != mc.level
                    || source.resident() != resident
                    || source.world() != world
                    || source.models() != models
                    || !source.link().equals(link)) {
                clear();
                Object wrapper = dh.wrapLevel.invoke(null, resident);
                Object level = dh.getClientLevel.invoke(world, wrapper);
                boolean owned = level == null;
                if (owned) level = dh.loadClientLevel.invoke(world, wrapper);
                if (level == null || !dh.serverClientLevel.isInstance(level)) return;
                source = new Source(link, mc.level, resident, world, wrapper, level, models, owned);
            }
            Object buffers = dh.getBuffers.invoke(source.level());
            if (buffers == null) {
                clear();
                return;
            }
            // DH's single-player tick updates only its active dimension. This tick loads
            // existing LOD data for the resident neighbor, with DH distant generation disabled.
            dh.tickModule.invoke(dh.clientModule.get(source.level()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    public static void beginFrame() {
        frame = null;
        composited = false;
    }

    public static void render(
            Object renderer, Object nativeParams, boolean opaque, Object profiler) {
        var active = source;
        var dh = binding;
        var mc = Minecraft.getInstance();
        if (active == null
                || dh == null
                || failed
                || active.viewer() != mc.level
                || active.models() != mc.getModelManager().getBlockStateModelSet()
                || IrisRenderCompat.isShaderPackInUse()
                || !MementoInAbyss.CONFIGS.graphsSection.crossDimensionLodEnabled.get()
                || !dh.nativeRenderer(renderer)) return;
        try {
            if (opaque) {
                if (frame != null || dh.defersTransparency()) return;
                Object nativeCamera = dh.camera.get(nativeParams);
                if (nativeCamera == null) return;
                Vec3 camera =
                        new Vec3(
                                dh.cameraX.getDouble(nativeCamera),
                                dh.cameraY.getDouble(nativeCamera),
                                dh.cameraZ.getDouble(nativeCamera));
                double gap = gap(active.link(), camera.y);
                int radius = CrossDimensionLodLinks.radius(active.link());
                // DH's near fade is for the active world's vanilla terrain. Keep the MIA
                // seam renderer wherever that fade could remove valid neighboring geometry.
                if (gap >= radius || gap <= dh.nearClip.getFloat(nativeParams) * 1.5) return;
                Object params = dh.parameters.newInstance();
                dh.copyParameters(params, nativeParams, active);
                dh.paramLevel.set(params, active.wrapper());
                dh.lightmap.set(params, dh.lightmap.get(nativeParams));
                dh.camera.set(
                        params,
                        dh.position.newInstance(
                                camera.x, camera.y - active.link().displayYOffset(), camera.z));
                dh.copyParams.invoke(dh.apiCopy.get(params), params);
                Object handler = dh.getBuffers.invoke(active.level());
                if (handler == null) return;
                Object list = dh.selectBuffers(handler, params, active.link(), camera, radius, gap);
                if (list == null) return;
                dh.render.invoke(renderer, params, true, list, profiler);
                frame = new Frame(active, params, list);
            } else if (frame != null && frame.source() == active && !composited) {
                dh.render.invoke(renderer, frame.params(), false, frame.buffers(), profiler);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    public static void composited() {
        composited = frame != null && frame.source() == source;
        if (composited && REPORTED_LINKS.add(frame.source().link().id())) {
            MementoInAbyss.LOGGER.info(
                    "Experimental DH neighbor LOD rendered {}", frame.source().link().id());
        }
    }

    public static boolean drawn(CrossDimensionLodLink link) {
        return composited && frame != null && frame.source().link().equals(link);
    }

    public static void clear() {
        var previous = source;
        source = null;
        beginFrame();
        if (previous == null
                || !previous.owned()
                || binding == null
                || Minecraft.getInstance().level == previous.resident()) return;
        try {
            if (binding.currentWorld.invoke(null) == previous.world())
                binding.unloadLevel.invoke(previous.world(), previous.wrapper());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            MementoInAbyss.LOGGER.warn("Failed to release experimental DH neighbor level", e);
        }
    }

    private static void disable(Throwable error) {
        failed = true;
        clear();
        MementoInAbyss.LOGGER.warn("Experimental DH neighbor LOD disabled", error);
    }

    private static boolean supported(CrossDimensionLodLink link) {
        return (link.source().equals(MiaDimensions.GREAT_FAULT_LEVEL)
                        && link.target().equals(MiaDimensions.THE_ABYSS_LEVEL))
                || (link.source().equals(MiaDimensions.THE_ABYSS_LEVEL)
                        && link.target().equals(MiaDimensions.GREAT_FAULT_LEVEL));
    }

    private static double gap(CrossDimensionLodLink link, double y) {
        return Math.max(
                0,
                Math.max(
                        link.sourceHeight().minY() + link.displayYOffset() - y,
                        y - link.sourceHeight().maxY() - link.displayYOffset()));
    }

    private record Source(
            CrossDimensionLodLink link,
            ClientLevel viewer,
            ClientLevel resident,
            Object world,
            Object wrapper,
            Object level,
            Object models,
            boolean owned) {}

    private record Frame(Source source, Object params, Object buffers) {}

    private static final class Binding {
        private final Class<?> api = type("api.DhApi");
        private final Class<?> clientWorld = type("core.world.IDhClientWorld");
        private final Class<?> levelWrapper = type("core.wrapperInterfaces.world.ILevelWrapper");
        private final Class<?> clientLevel = type("core.level.IDhClientLevel");
        private final Class<?> serverClientLevel = type("core.level.DhClientServerLevel");
        private final Class<?> apiParam =
                type("api.methods.events.sharedParameterObjects.DhApiRenderParam");
        private final Class<?> param = type("core.render.RenderParams");
        private final Class<?> vector = type("core.util.math.DhVec3d");
        private final Class<?> bufferHandler = type("core.render.RenderBufferHandler");
        private final Class<?> buffer =
                type("core.dataObjects.render.bufferBuilding.LodBufferContainer");
        private final Class<?> bufferList = type("core.util.objects.SortedArraySet");
        private final Class<?> tree = type("core.render.QuadTree.LodQuadTree");
        private final Class<?> renderSection = type("core.render.QuadTree.LodRenderSection");
        private final Class<?> sectionPos = type("core.pos.DhSectionPos");
        private final Class<?> delayed = type("api.DhApi$Delayed");
        private final Field configs = delayed.getField("configs");
        private final Field renderProxy = delayed.getField("renderProxy");
        private final Method deferTransparency =
                renderProxy.getType().getMethod("getDeferTransparentRendering");
        private final Method worldConfig =
                type("api.interfaces.config.IDhApiConfig").getMethod("worldGenerator");
        private final Method generation =
                type("api.interfaces.config.both.IDhApiWorldGenerationConfig")
                        .getMethod("enableDistantWorldGeneration");
        private final Method configValue =
                type("api.interfaces.config.IDhApiConfigValue").getMethod("getValue");
        private final Method currentWorld =
                type("core.api.internal.SharedApi").getMethod("tryGetDhClientWorld");
        private final Method wrapLevel =
                type("common.wrappers.world.ClientLevelWrapper")
                        .getMethod("getWrapper", ClientLevel.class);
        private final Method getClientLevel = clientWorld.getMethod("getClientLevel", levelWrapper);
        private final Method loadClientLevel =
                clientWorld.getMethod("getOrLoadClientLevel", levelWrapper);
        private final Method unloadLevel = clientWorld.getMethod("unloadLevel", levelWrapper);
        private final Field clientModule = serverClientLevel.getField("clientside");
        private final Method tickModule = clientModule.getType().getMethod("clientTick");
        private final Method getBuffers = clientLevel.getMethod("getRenderBufferHandler");
        private final Constructor<?> parameters = param.getConstructor();
        private final Constructor<?> position =
                vector.getConstructor(double.class, double.class, double.class);
        private final Field camera = param.getField("exactCameraPosition");
        private final Field cameraX = vector.getField("x");
        private final Field cameraY = vector.getField("y");
        private final Field cameraZ = vector.getField("z");
        private final Field nearClip = param.getField("nearClipPlane");
        private final Class<?> matrix = type("api.objects.math.DhApiMat4f");
        private final Field renderPass = apiParam.getField("renderPass");
        private final Field partialTicks = apiParam.getField("partialTicks");
        private final Field farClip = apiParam.getField("farClipPlane");
        private final Field mcProjection = apiParam.getField("mcProjectionMatrix");
        private final Field mcModelView = apiParam.getField("mcModelViewMatrix");
        private final Field dhProjection = apiParam.getField("dhProjectionMatrix");
        private final Field dhModelView = apiParam.getField("dhModelViewMatrix");
        private final Field paramLevel = param.getField("clientLevelWrapper");
        private final Field lightmap = param.getField("lightmap");
        private final Field apiCopy = param.getField("apiCopy");
        private final Method copyParams = apiParam.getMethod("update", apiParam);
        private final Method initParams =
                apiParam.getMethod(
                        "update",
                        renderPass.getType(),
                        float.class,
                        float.class,
                        float.class,
                        matrix,
                        matrix,
                        matrix,
                        matrix,
                        int.class,
                        type("api.interfaces.world.IDhApiLevelWrapper"));
        private final Method matrixValues = matrix.getMethod("putValuesInArray", float[].class);
        private final Field quadTree = bufferHandler.getDeclaredField("lodQuadTree");
        private final Method enabledSections =
                tree.getMethod("populateListWithEnabledRenderSections", ArrayList.class);
        private final Field sectionBuffer = renderSection.getField("renderBufferContainer");
        private final Method rendering = renderSection.getMethod("getRenderingEnabled");
        private final Method add = bufferList.getMethod("add", Object.class);
        private final Constructor<?> sorted = bufferList.getConstructor(Comparator.class);
        private final Field bufferPos = buffer.getField("pos");
        private final Field uploaded = buffer.getField("buffersUploaded");
        private final Method hasVbos = buffer.getMethod("hasNonNullVbos");
        private final Method minX = sectionPos.getMethod("getMinCornerBlockX", long.class);
        private final Method minZ = sectionPos.getMethod("getMinCornerBlockZ", long.class);
        private final Method width = sectionPos.getMethod("getBlockWidth", long.class);
        private final Method render =
                type("core.wrapperInterfaces.render.renderPass.IDhTerrainRenderer")
                        .getMethod(
                                "render",
                                param,
                                boolean.class,
                                bufferList,
                                type("core.wrapperInterfaces.minecraft.IProfilerWrapper"));

        private final ArrayList<Object> sections = new ArrayList<>();
        private final float[] matrixArray = new float[16];
        private final Matrix4f modelView = new Matrix4f();
        private final Matrix4f projection = new Matrix4f();
        private final FrustumIntersection frustum = new FrustumIntersection();

        private Binding() throws ReflectiveOperationException {
            quadTree.setAccessible(true);
            if (!"3.3.3".equals(api.getMethod("getModVersion").invoke(null))
                    || (int) api.getMethod("getApiMajorVersion").invoke(null) != 7
                    || (int) api.getMethod("getApiMinorVersion").invoke(null) != 2)
                throw new UnsupportedOperationException(
                        "Experimental adapter requires DH 3.3.3 / API 7.2");
        }

        private void copyParameters(Object destination, Object original, Source source)
                throws ReflectiveOperationException {
            initParams.invoke(
                    destination,
                    renderPass.get(original),
                    partialTicks.getFloat(original),
                    nearClip.getFloat(original),
                    farClip.getFloat(original),
                    mcProjection.get(original),
                    mcModelView.get(original),
                    dhProjection.get(original),
                    dhModelView.get(original),
                    source.link().sourceHeight().minY(),
                    source.wrapper());
        }

        private boolean defersTransparency() throws ReflectiveOperationException {
            Object proxy = renderProxy.get(null);
            return proxy == null || (boolean) deferTransparency.invoke(proxy);
        }

        private boolean generatesDistantTerrain(Object config) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(
                    configValue.invoke(generation.invoke(worldConfig.invoke(config))));
        }

        private boolean nativeRenderer(Object renderer) {
            String name = renderer.getClass().getName();
            return name.equals(DH + "common.render.blaze.BlazeDhTerrainRenderer")
                    || name.equals(DH + "common.render.openGl.GlDhTerrainRenderer");
        }

        private @Nullable Object selectBuffers(
                Object handler,
                Object params,
                CrossDimensionLodLink link,
                Vec3 camera,
                int radius,
                double gap)
                throws ReflectiveOperationException {
            matrixValues.invoke(dhProjection.get(params), matrixArray);
            projection.setTransposed(matrixArray);
            matrixValues.invoke(dhModelView.get(params), matrixArray);
            frustum.set(projection.mul(modelView.setTransposed(matrixArray)));

            double radiusSquared = (double) radius * radius - gap * gap;
            int lowY = link.sourceHeight().minY() + link.displayYOffset();
            int highY = link.sourceHeight().maxY() + link.displayYOffset();
            var distances = new IdentityHashMap<Object, Double>();
            var coveredChunks = new LongOpenHashSet();
            try {
                enabledSections.invoke(quadTree.get(handler), sections);
                for (Object section : sections) {
                    if (!(boolean) rendering.invoke(section)) continue;
                    Object item = sectionBuffer.get(section);
                    if (item == null || !uploaded.getBoolean(item)) continue;
                    long position = bufferPos.getLong(item);
                    int x = (int) minX.invoke(null, position);
                    int z = (int) minZ.invoke(null, position);
                    int w = (int) width.invoke(null, position);
                    double dx = Math.max(Math.abs(camera.x - x), Math.abs(camera.x - (x + w)));
                    double dz = Math.max(Math.abs(camera.z - z), Math.abs(camera.z - (z + w)));
                    if (dx * dx + dz * dz > radiusSquared
                            || !visible(camera, x, lowY, z, x + w, highY, z + w)) continue;
                    if ((boolean) hasVbos.invoke(item)) {
                        double centerX = x + w * 0.5 - camera.x;
                        double centerZ = z + w * 0.5 - camera.z;
                        distances.put(item, centerX * centerX + centerZ * centerZ);
                    }
                    for (int cx = x >> 4; cx < ((x + w) >> 4); cx++) {
                        for (int cz = z >> 4; cz < ((z + w) >> 4); cz++) {
                            coveredChunks.add(ChunkPos.pack(cx, cz));
                        }
                    }
                }
            } finally {
                sections.clear();
            }
            // If any visible MIA terrain falls outside the uploaded DH tiles, keep the
            // entire link on MIA. This also keeps its depth and transparent pass consistent.
            if (distances.isEmpty()
                    || !CrossDimensionLodRenderer.coversLink(
                            link, camera, radius, bounds -> covered(camera, bounds, coveredChunks)))
                return null;
            var order = new IdentityHashMap<Object, Integer>();
            var ordered = new ArrayList<>(distances.keySet());
            ordered.sort(Comparator.comparingDouble(distances::get));
            Object result = sorted.newInstance(Comparator.comparingInt(order::get));
            for (Object item : ordered) {
                order.put(item, order.size());
                add.invoke(result, item);
            }
            return result;
        }

        private boolean covered(Vec3 camera, AABB bounds, LongOpenHashSet chunks) {
            if (!visible(
                    camera,
                    bounds.minX,
                    bounds.minY,
                    bounds.minZ,
                    bounds.maxX,
                    bounds.maxY,
                    bounds.maxZ)) return true;
            int minX = (int) Math.floor(bounds.minX) >> 4;
            int minZ = (int) Math.floor(bounds.minZ) >> 4;
            int maxX = Math.max(minX, ((int) Math.ceil(bounds.maxX) - 1) >> 4);
            int maxZ = Math.max(minZ, ((int) Math.ceil(bounds.maxZ) - 1) >> 4);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!chunks.contains(ChunkPos.pack(x, z))
                            && visible(
                                    camera,
                                    x * 16.0,
                                    bounds.minY,
                                    z * 16.0,
                                    (x + 1) * 16.0,
                                    bounds.maxY,
                                    (z + 1) * 16.0)) return false;
                }
            }
            return true;
        }

        private boolean visible(
                Vec3 camera, double x0, double y0, double z0, double x1, double y1, double z1) {
            return frustum.testAab(
                    (float) (x0 - camera.x),
                    (float) (y0 - camera.y),
                    (float) (z0 - camera.z),
                    (float) (x1 - camera.x),
                    (float) (y1 - camera.y),
                    (float) (z1 - camera.z));
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(DH + name, false, DhCrossDimensionLod.class.getClassLoader());
        }
    }

    private DhCrossDimensionLod() {}
}
