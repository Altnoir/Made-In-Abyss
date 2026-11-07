package com.altnoir.mementoinabyss.compat.dh;

import com.altnoir.mementoinabyss.MementoInAbyss;
import com.altnoir.mementoinabyss.compat.MiaMods;
import com.altnoir.mementoinabyss.compat.iris.IrisRenderCompat;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLink;
import com.altnoir.mementoinabyss.impl.lod.server.CrossDimensionLodLinks;
import com.altnoir.mementoinabyss.impl.vertical.client.VerticalResidentWorlds;
import com.altnoir.mementoinabyss.infrastructure.worldgen.dimension.MiaDimensions;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Optional adapter for the terrain pass and level registry in DH 3.3.3. */
public final class DhCrossDimensionLod {
    private static final String DH = "com.seibel.distanthorizons.";
    private static final boolean REQUESTED =
            Boolean.parseBoolean(System.getProperty("mia.experimentalDhCrossDimensionLod", "true"));
    private static final HashSet<Identifier> REPORTED_LINKS = new HashSet<>();
    private static @Nullable Binding binding;
    private static final Map<Identifier, Source> SOURCES = new HashMap<>();
    private static final ArrayList<Frame> FRAMES = new ArrayList<>();
    private static boolean composited;
    private static boolean failed;
    private static int ticks;

    public static boolean replacesMiaRenderer() {
        return REQUESTED && MiaMods.DISTANTHORIZONS.isLoaded();
    }

    public static void tick(boolean currentTerrainReady) {
        if (!REQUESTED || !MiaMods.DISTANTHORIZONS.isLoaded()) return;
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (failed
                || mc.level == null
                || mc.player == null
                || server == null
                || server.isPublished()
                || !MementoInAbyss.CONFIGS.graphsSection.crossDimensionLodEnabled.get()) {
            clear();
            return;
        }
        if (++ticks % 5 != 0) return;
        if (!CrossDimensionLodLinks.activeAt(mc.level, mc.player.getX(), mc.player.getZ())) {
            clear();
            return;
        }
        if (!currentTerrainReady) return;
        var links =
                CrossDimensionLodLinks.forTargets(mc.level).stream()
                        .filter(DhCrossDimensionLod::supported)
                        .filter(
                                link ->
                                        gap(link, mc.player.getY())
                                                < CrossDimensionLodLinks.radius(link))
                        .toList();
        try {
            if (links.isEmpty()) {
                clear();
                return;
            }
            if (binding == null) binding = new Binding();
            var dh = binding;
            Object world = dh.currentWorld.invoke(null);
            if (world == null) {
                clear();
                return;
            }
            var models = mc.getModelManager().getBlockStateModelSet();
            var iterator = SOURCES.values().iterator();
            while (iterator.hasNext()) {
                var previous = iterator.next();
                var resident = VerticalResidentWorlds.residentLevel(previous.link().source());
                if (!links.contains(previous.link())
                        || previous.viewer() != mc.level
                        || previous.world() != world
                        || previous.models() != models
                        || previous.radius() != CrossDimensionLodLinks.radius(previous.link())
                        || (resident != null && resident != previous.resident())) {
                    iterator.remove();
                    release(previous);
                }
            }
            for (var link : links) {
                var active = SOURCES.get(link.id());
                if (active == null) {
                    active = createSource(link, world, models);
                    if (active == null) continue;
                    SOURCES.put(link.id(), active);
                }
                dh.markAccessed.invoke(active.wrapper());
                dh.tickTree.invoke(
                        active.tree(),
                        dh.blockPosition.newInstance(mc.player.getBlockX(), mc.player.getBlockZ()));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    private static @Nullable Source createSource(
            CrossDimensionLodLink link, Object world, Object models)
            throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var resident = VerticalResidentWorlds.residentLevel(link.source());
        boolean privateView = resident == null;
        if (privateView) {
            var server = mc.getSingleplayerServer();
            var level = server == null ? null : server.getLevel(link.source());
            if (level == null) return null;
            // This metadata view gives DH the real dimension and hashed seed. It has no
            // dedicated renderer, chunk tickets, section stream, or ticking simulation.
            resident =
                    new ClientLevel(
                            mc.getConnection(),
                            new ClientLevel.ClientLevelData(
                                    mc.level.getDifficulty(),
                                    mc.level.getLevelData().isHardcore(),
                                    false),
                            link.source(),
                            Holder.direct(link.sourceType()),
                            2,
                            2,
                            mc.levelRenderer,
                            level.isDebug(),
                            BiomeManager.obfuscateSeed(level.getSeed()),
                            level.getSeaLevel());
        }
        boolean installed = false;
        try {
            Object wrapper = binding.wrapLevel.invoke(null, resident);
            Object level = binding.getClientLevel.invoke(world, wrapper);
            boolean owned = level == null;
            if (owned) level = binding.loadClientLevel.invoke(world, wrapper);
            if (level == null || !binding.serverClientLevel.isInstance(level)) return null;
            int radius = CrossDimensionLodLinks.radius(link);
            Object provider = binding.getProvider.invoke(level);
            Object renderLevel = binding.sourceLevel(level, wrapper);
            // A separate bounded tree shares DH's source data without changing its active
            // renderer or using the global distant-generation radius for a neighbor.
            Object tree =
                    binding.treeConstructor.newInstance(
                            renderLevel,
                            radius * 2,
                            mc.player.getBlockX(),
                            mc.player.getBlockZ(),
                            provider);
            Object updates = binding.dataListener(tree);
            try {
                binding.addDataListener.invoke(provider, updates);
                installed = true;
                return new Source(
                        link,
                        mc.level,
                        resident,
                        world,
                        wrapper,
                        level,
                        models,
                        owned,
                        privateView,
                        radius,
                        provider,
                        tree,
                        updates);
            } finally {
                if (!installed) {
                    binding.closeTree.invoke(tree);
                    if (owned) binding.unloadLevel.invoke(world, wrapper);
                }
            }
        } finally {
            if (!installed && privateView) NeoForge.EVENT_BUS.post(new LevelEvent.Unload(resident));
        }
    }

    public static void beginFrame() {
        FRAMES.clear();
        composited = false;
    }

    public static void render(
            Object renderer, Object nativeParams, boolean opaque, Object profiler) {
        var dh = binding;
        var mc = Minecraft.getInstance();
        if (dh == null
                || failed
                || mc.level == null
                || mc.getSingleplayerServer() == null
                || mc.getSingleplayerServer().isPublished()
                || !MementoInAbyss.CONFIGS.graphsSection.crossDimensionLodEnabled.get()
                || !dh.nativeRenderer(renderer)) return;
        try {
            if (opaque) {
                FRAMES.clear();
                Object nativeCamera = dh.camera.get(nativeParams);
                if (nativeCamera == null) return;
                Vec3 camera =
                        new Vec3(
                                dh.cameraX.getDouble(nativeCamera),
                                dh.cameraY.getDouble(nativeCamera),
                                dh.cameraZ.getDouble(nativeCamera));
                if (!CrossDimensionLodLinks.activeAt(mc.level, camera.x, camera.z)) return;
                for (var active : SOURCES.values()) {
                    if (active.viewer() != mc.level
                            || active.models() != mc.getModelManager().getBlockStateModelSet())
                        continue;
                    double gap = gap(active.link(), camera.y);
                    int radius = CrossDimensionLodLinks.radius(active.link());
                    if (gap >= radius) continue;
                    Object params = dh.parameters.newInstance();
                    dh.copyParameters(params, nativeParams, active);
                    dh.paramLevel.set(params, active.wrapper());
                    dh.lightmap.set(params, dh.lightmap.get(nativeParams));
                    dh.camera.set(
                            params,
                            dh.position.newInstance(
                                    camera.x, camera.y - active.link().displayYOffset(), camera.z));
                    dh.copyParameters(dh.apiCopy.get(params), params, active);
                    Object list =
                            dh.selectBuffers(
                                    active.tree(), params, active.link(), camera, radius, gap);
                    if (list == null) continue;
                    dh.renderNeighbor(renderer, params, nativeParams, true, list, profiler);
                    FRAMES.add(new Frame(active, params, list));
                }
            } else {
                for (var frame : FRAMES) {
                    dh.copyParameters(frame.params(), nativeParams, frame.source());
                    dh.paramLevel.set(frame.params(), frame.source().wrapper());
                    dh.lightmap.set(frame.params(), dh.lightmap.get(nativeParams));
                    Object nativeCamera = dh.camera.get(nativeParams);
                    if (nativeCamera == null) continue;
                    dh.camera.set(
                            frame.params(),
                            dh.position.newInstance(
                                    dh.cameraX.getDouble(nativeCamera),
                                    dh.cameraY.getDouble(nativeCamera)
                                            - frame.source().link().displayYOffset(),
                                    dh.cameraZ.getDouble(nativeCamera)));
                    dh.copyParameters(
                            dh.apiCopy.get(frame.params()), frame.params(), frame.source());
                    dh.renderNeighbor(
                            renderer,
                            frame.params(),
                            nativeParams,
                            false,
                            frame.buffers(),
                            profiler);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    public static void composited() {
        composited = !FRAMES.isEmpty();
        for (var frame : FRAMES) {
            if (REPORTED_LINKS.add(frame.source().link().id()))
                MementoInAbyss.LOGGER.info(
                        "Experimental DH neighbor LOD rendered {}", frame.source().link().id());
        }
    }

    public static boolean drawn(CrossDimensionLodLink link) {
        if (!composited) return false;
        for (var frame : FRAMES) {
            if (frame.source().link().equals(link) && SOURCES.get(link.id()) == frame.source())
                return true;
        }
        return false;
    }

    public static void clear() {
        beginFrame();
        for (var source : SOURCES.values()) release(source);
        SOURCES.clear();
    }

    private static void release(Source previous) {
        try {
            if (binding != null) {
                try {
                    binding.removeDataListener.invoke(previous.provider(), previous.updates());
                } finally {
                    binding.closeTree.invoke(previous.tree());
                }
            }
            if (Minecraft.getInstance().level == previous.resident()) return;
            if (previous.owned()
                    && binding != null
                    && binding.currentWorld.invoke(null) == previous.world())
                binding.unloadLevel.invoke(previous.world(), previous.wrapper());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            MementoInAbyss.LOGGER.warn("Failed to release experimental DH neighbor level", e);
        } finally {
            if (previous.privateView() && Minecraft.getInstance().level != previous.resident())
                NeoForge.EVENT_BUS.post(new LevelEvent.Unload(previous.resident()));
        }
    }

    private static void disable(Throwable error) {
        failed = true;
        beginFrame();
        MementoInAbyss.LOGGER.warn("Experimental DH neighbor LOD disabled", error);
    }

    private static boolean supported(CrossDimensionLodLink link) {
        return (link.source().equals(MiaDimensions.THE_ABYSS_LEVEL)
                        && (link.target().equals(MiaDimensions.GREAT_FAULT_LEVEL)
                                || link.target().equals(Level.OVERWORLD)))
                || (link.target().equals(MiaDimensions.THE_ABYSS_LEVEL)
                        && (link.source().equals(MiaDimensions.GREAT_FAULT_LEVEL)
                                || link.source().equals(Level.OVERWORLD)));
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
            boolean owned,
            boolean privateView,
            int radius,
            Object provider,
            Object tree,
            Object updates) {}

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
        private final Class<?> buffer =
                type("core.dataObjects.render.bufferBuilding.LodBufferContainer");
        private final Class<?> bufferList = type("core.util.objects.SortedArraySet");
        private final Class<?> tree = type("core.render.QuadTree.LodQuadTree");
        private final Class<?> renderSection = type("core.render.QuadTree.LodRenderSection");
        private final Class<?> sectionPos = type("core.pos.DhSectionPos");
        private final Method currentWorld =
                type("core.api.internal.SharedApi").getMethod("tryGetDhClientWorld");
        private final Method wrapLevel =
                type("common.wrappers.world.ClientLevelWrapper")
                        .getMethod("getWrapper", ClientLevel.class);
        private final Method getClientLevel = clientWorld.getMethod("getClientLevel", levelWrapper);
        private final Method loadClientLevel =
                clientWorld.getMethod("getOrLoadClientLevel", levelWrapper);
        private final Method unloadLevel = clientWorld.getMethod("unloadLevel", levelWrapper);
        private final Method markAccessed =
                type("core.wrapperInterfaces.world.IClientLevelWrapper").getMethod("markAccessed");
        private final Class<?> providerType =
                type("core.file.fullDatafile.V2.FullDataSourceProviderV2");
        private final Class<?> listenerType =
                type("core.file.fullDatafile.IDataSourceUpdateListenerFunc");
        private final Class<?> blockPositionType = type("core.pos.blockPos.DhBlockPos2D");
        private final Method getProvider = clientLevel.getMethod("getFullDataProvider");
        private final Constructor<?> treeConstructor =
                tree.getConstructor(clientLevel, int.class, int.class, int.class, providerType);
        private final Constructor<?> blockPosition =
                blockPositionType.getConstructor(int.class, int.class);
        private final Method tickTree = tree.getMethod("tryTick", blockPositionType);
        private final Method closeTree = tree.getMethod("close");
        private final Method reloadSection = tree.getMethod("queuePosToReload", long.class);
        private final Method dataPosition =
                type("core.dataObjects.fullData.sources.FullDataSourceV2").getMethod("getPos");
        private final Method addDataListener =
                providerType.getMethod("addDataSourceUpdateListener", listenerType);
        private final Method removeDataListener =
                providerType.getMethod("removeDataSourceUpdateListener", listenerType);
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
        private final Class<?> glRenderer = type("common.render.openGl.GlDhTerrainRenderer");
        private final Method glShader = glRenderer.getMethod("getTerrainShaderProgram");
        private final Method fillShader =
                type("api.interfaces.override.rendering.IDhApiShaderProgram")
                        .getMethod("fillUniformData", apiParam);
        private final Field glFrameIndex =
                type("common.render.openGl.terrain.GlDhTerrainShaderProgram")
                        .getDeclaredField("frameIndexMod8");
        private final Field glClipDistance =
                type("common.render.openGl.terrain.GlDhTerrainShaderProgram")
                        .getField("uClipDistance");
        private final Field glDither =
                type("common.render.openGl.terrain.GlDhTerrainShaderProgram")
                        .getField("uDitherDhRendering");
        private final Method glFloatUniform =
                type("common.render.openGl.glObject.shader.GlShaderProgram")
                        .getMethod("setUniform", int.class, float.class);
        private final Method glBooleanUniform =
                type("common.render.openGl.glObject.shader.GlShaderProgram")
                        .getMethod("setUniform", int.class, boolean.class);
        private final Field blazeFrameIndex =
                type("common.render.blaze.BlazeDhTerrainRenderer")
                        .getDeclaredField("frameIndexMod8");

        private final ArrayList<Object> sections = new ArrayList<>();
        private final float[] matrixArray = new float[16];
        private final Matrix4f modelView = new Matrix4f();
        private final Matrix4f projection = new Matrix4f();
        private final FrustumIntersection frustum = new FrustumIntersection();

        private Binding() throws ReflectiveOperationException {
            glFrameIndex.setAccessible(true);
            blazeFrameIndex.setAccessible(true);
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
                    source.link().sourceHeight().minY() + source.link().displayYOffset(),
                    source.wrapper());
        }

        private void renderNeighbor(
                Object renderer,
                Object params,
                Object original,
                boolean opaque,
                Object buffers,
                Object profiler)
                throws ReflectiveOperationException {
            if (IrisRenderCompat.isShaderPackInUse()) {
                render.invoke(renderer, params, opaque, buffers, profiler);
                return;
            }
            if (glRenderer.isInstance(renderer)) {
                Object shader = glShader.invoke(renderer);
                int frame = glFrameIndex.getInt(shader);
                try {
                    // DH shares this shader across terrain passes. Keep the native frame's
                    // jitter while installing the neighbor's height and matrix uniforms.
                    glFrameIndex.setInt(shader, frame < 0 ? frame : (frame + 7) % 8);
                    fillShader.invoke(shader, params);
                    // Neighbor LOD has no vanilla terrain to fade into in this dimension.
                    glFloatUniform.invoke(shader, glClipDistance.getInt(shader), 0.0F);
                    glBooleanUniform.invoke(shader, glDither.getInt(shader), false);
                    render.invoke(renderer, params, opaque, buffers, profiler);
                } finally {
                    try {
                        glFrameIndex.setInt(shader, frame < 0 ? frame : (frame + 7) % 8);
                        fillShader.invoke(shader, original);
                    } finally {
                        glFrameIndex.setInt(shader, frame);
                    }
                }
            } else {
                int frame = blazeFrameIndex.getInt(renderer);
                try {
                    blazeFrameIndex.setInt(renderer, opaque ? (frame + 7) % 8 : frame);
                    render.invoke(renderer, params, opaque, buffers, profiler);
                } finally {
                    blazeFrameIndex.setInt(renderer, frame);
                }
            }
        }

        private Object dataListener(Object sourceTree) {
            return Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[] {listenerType},
                    (proxy, method, args) ->
                            switch (method.getName()) {
                                case "OnDataSourceUpdated" -> {
                                    reloadSection.invoke(sourceTree, dataPosition.invoke(args[0]));
                                    yield null;
                                }
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "toString" -> "MIA neighbor DH data listener";
                                default ->
                                        throw new UnsupportedOperationException(method.getName());
                            });
        }

        private Object sourceLevel(Object level, Object wrapper) {
            // DH's integrated-server level returns Minecraft's active client wrapper.
            // A render-only view pins asynchronous mesh construction to the source world.
            return Proxy.newProxyInstance(
                    clientLevel.getClassLoader(),
                    new Class<?>[] {clientLevel},
                    (proxy, method, args) ->
                            switch (method.getName()) {
                                case "getClientLevelWrapper" -> wrapper;
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "toString" -> "MIA neighbor DH render level";
                                default -> method.invoke(level, args);
                            });
        }

        private boolean nativeRenderer(Object renderer) {
            String name = renderer.getClass().getName();
            return name.equals(DH + "common.render.blaze.BlazeDhTerrainRenderer")
                    || name.equals(DH + "common.render.openGl.GlDhTerrainRenderer");
        }

        private @Nullable Object selectBuffers(
                Object sourceTree,
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
            boolean shadowPass = IrisRenderCompat.isRenderingShadowPass();
            int lowY = link.sourceHeight().minY() + link.displayYOffset();
            int highY = link.sourceHeight().maxY() + link.displayYOffset();
            var distances = new IdentityHashMap<Object, Double>();
            try {
                enabledSections.invoke(sourceTree, sections);
                for (Object section : sections) {
                    if (!(boolean) rendering.invoke(section)) continue;
                    Object item = sectionBuffer.get(section);
                    if (item == null || !uploaded.getBoolean(item)) continue;
                    long position = bufferPos.getLong(item);
                    int x = (int) minX.invoke(null, position);
                    int z = (int) minZ.invoke(null, position);
                    int w = (int) width.invoke(null, position);
                    double dx = Math.max(Math.max(x - camera.x, 0), camera.x - (x + w));
                    double dz = Math.max(Math.max(z - camera.z, 0), camera.z - (z + w));
                    if (dx * dx + dz * dz > radiusSquared
                            || (!shadowPass && !visible(camera, x, lowY, z, x + w, highY, z + w)))
                        continue;
                    if ((boolean) hasVbos.invoke(item)) {
                        double centerX = x + w * 0.5 - camera.x;
                        double centerZ = z + w * 0.5 - camera.z;
                        distances.put(item, centerX * centerX + centerZ * centerZ);
                    }
                }
            } finally {
                sections.clear();
            }
            if (distances.isEmpty()) return null;
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
