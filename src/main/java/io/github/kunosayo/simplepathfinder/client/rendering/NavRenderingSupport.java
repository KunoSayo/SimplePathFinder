package io.github.kunosayo.simplepathfinder.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.client.ClientNavDataManager;
import io.github.kunosayo.simplepathfinder.client.event.NavigationRenderTriggerEvent;
import io.github.kunosayo.simplepathfinder.data.LevelNavDataSavedData;
import io.github.kunosayo.simplepathfinder.init.ModItems;
import io.github.kunosayo.simplepathfinder.nav.LevelNavData;
import io.github.kunosayo.simplepathfinder.nav.ModNavResult;
import io.github.kunosayo.simplepathfinder.nav.NavResult;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NavRenderingSupport {
    public static final NavRenderingSupport INSTANCE = new NavRenderingSupport();

    private static final double EPSILON = 1.0E-6;
    private static final double PATH_RDP_TOLERANCE = 0.6;
    private static final int PATH_CHAIKIN_ITERATIONS = 2;
    private static final float DEFAULT_BOX_SIZE = 0.4f;
    private static final float DEBUG_BOX_SIZE = 0.4f;

    private final List<IRenderElement> elements = new ArrayList<>();
    private final List<Line> lineElements = new ArrayList<>();
    private boolean linesDirty = false;

    public void prepare() {
        elements.clear();
        lineElements.clear();
        linesDirty = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        NavigationRenderTriggerEvent renderNavEvent = new NavigationRenderTriggerEvent(player);
        if (NeoForge.EVENT_BUS.post(renderNavEvent).isCanceled()) return;

        NavResult clientNavResult = SimplePathFinder.clientNavResult.get();
        if (clientNavResult != null && clientNavResult.modNavResult != null) {
            this.prepareNavigationPath(clientNavResult.modNavResult);
        }

        this.prepareDebug();
        this.prepareLines();
    }

    public void prepareDebug() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!(player.getMainHandItem().is(ModItems.DEBUG_NAV) || player.getMainHandItem().is(ModItems.NAVIGATION))) {
            return;
        }

        var level = player.level();
        LevelNavData data;
        if (level instanceof ServerLevel serverLevel) {
            data = LevelNavDataSavedData.loadFromLevel(serverLevel).levelNavData;
        } else {
            data = ClientNavDataManager.getNavDataForPlayer();
        }
        if (data == null) return;

        int amount = player.getMainHandItem().getCount();
        if (amount == 64 || amount == 63) {
            return;
        }

        int layerRangeLeft;
        int layerRangeRight = Integer.MAX_VALUE;
        if (amount > 16 && amount <= 48) {
            layerRangeRight = amount - 32;
            layerRangeLeft = layerRangeRight;
        } else {
            layerRangeLeft = Integer.MIN_VALUE;
        }
        amount = Math.clamp(amount, 3, 16);
        var currentChunkPos = ChunkPos.containing(player.blockPosition());

        for (int offsetX = -amount; offsetX <= amount; offsetX++) {
            for (int offsetZ = -amount; offsetZ <= amount; offsetZ++) {
                int distance = Math.abs(offsetX) + Math.abs(offsetZ);
                if (distance >= amount) {
                    continue;
                }

                var chunkPos = new ChunkPos(currentChunkPos.x() + offsetX, currentChunkPos.z() + offsetZ);
                int finalLayerRangeRight = layerRangeRight;
                data.getNavChunk(chunkPos, false).ifPresent(navChunk -> {
                    for (ILayeredNavChunk layer : navChunk.getLayersCollection()) {
                        if (layer.getLayer() > finalLayerRangeRight || layer.getLayer() < layerRangeLeft) {
                            continue;
                        }
                        int layerColor = layerColor(layer.getLayer());
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int y = layer.getWalkY(x, z);
                                if (!layer.isWalkYValid(y)) {
                                    continue;
                                }

                                var blockPos = new BlockPos(
                                        chunkPos.getBlockX(x),
                                        y,
                                        chunkPos.getBlockZ(z)
                                );

                                if (layer.getDistance(x, z, false) < 0) {
                                    filledBox(
                                            new Vec3(blockPos.getX() + 1.0, blockPos.getY(), blockPos.getZ() + 0.5),
                                            DEBUG_BOX_SIZE,
                                            0x55ff0101
                                    );
                                }
                                if (layer.getDistance(x, z, true) < 0) {
                                    filledBox(
                                            new Vec3(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 1.0),
                                            DEBUG_BOX_SIZE,
                                            0x55ff0101
                                    );
                                }
                                filledBox(
                                        blockPos.getCenter(),
                                        DEBUG_BOX_SIZE,
                                        layerColor
                                );
                            }
                        }
                    }
                });
            }
        }
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        Vec3 position = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        poseStack.translate(position.scale(-1));
        for (IRenderElement element : elements) {
            element.render(poseStack, collector);
        }
        for (IRenderElement element : lineElements) {
            element.render(poseStack, collector);
        }
        poseStack.popPose();
    }

    public void filledBox(Vec3 center, int color) {
        filledBox(center, DEFAULT_BOX_SIZE, color);
    }

    public void filledBox(Vec3 center, float size, int color) {
        if (size <= 0.0f) {
            return;
        }
        elements.add(new FilledBox(center, size, color));
    }

    public void line(Vec3 start, Vec3 end, int color) {
        line(start, end, color, color);
    }

    public void line(Vec3 start, Vec3 end, int startColor, int endColor) {
        if (start.equals(end)) {
            return;
        }
        elements.add(new Line(start, end, 5, startColor, endColor));
        linesDirty = true;
    }

    private void prepareLines() {
        if (!linesDirty) {
            return;
        }
        lineElements.clear();
        List<Line> extractedLines = extractLines();
        List<Vec3> points = extractContinuousPoints(extractedLines);
        if (points.size() >= 2) {
            List<Vec3> smoothedPoints = PathCurveSmoother.smoothPath(
                    points,
                    PATH_RDP_TOLERANCE,
                    PATH_CHAIKIN_ITERATIONS
            );
            rebuildLineElements(smoothedPoints, extractedLines.getFirst());
        }
        linesDirty = false;
    }

    private List<Line> extractLines() {
        List<Line> extractedLines = new ArrayList<>();
        Iterator<IRenderElement> iterator = elements.iterator();
        while (iterator.hasNext()) {
            IRenderElement element = iterator.next();
            if (element instanceof Line line) {
                extractedLines.add(line);
                iterator.remove();
            }
        }
        return extractedLines;
    }

    private List<Vec3> extractContinuousPoints(List<Line> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }

        List<Vec3> points = new ArrayList<>();
        Line first = lines.getFirst();
        points.add(first.start());
        points.add(first.end());
        for (int index = 1; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (!samePosition(points.getLast(), line.start())) {
                points.add(line.start());
            }
            points.add(line.end());
        }
        return points;
    }

    private void rebuildLineElements(List<Vec3> points, Line sourceLine) {
        int lineCount = points.size() - 1;
        for (int index = 1; index < points.size(); index++) {
            double startRatio = (double) (index - 1) / lineCount;
            double endRatio = (double) index / lineCount;
            lineElements.add(new Line(
                    points.get(index - 1),
                    points.get(index),
                    sourceLine.thickness(),
                    colorFromRatio(startRatio, true),
                    colorFromRatio(endRatio, true)
            ));
        }
    }

    private boolean samePosition(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) < EPSILON * EPSILON;
    }

    public void prepareNavigationPath(ModNavResult navResult) {
        var path = navResult.posInThePath;
        int lineCount = path.size() - 1;
        for (int index = 1; index < path.size(); index++) {
            double startRatio = (double) (index - 1) / lineCount;
            double endRatio = (double) index / lineCount;
            line(
                    path.get(index - 1).getCenter(),
                    path.get(index).getCenter(),
                    colorFromRatio(startRatio, true),
                    colorFromRatio(endRatio, true)
            );
        }
    }

    private boolean hasNavData(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            return LevelNavDataSavedData.loadFromLevel(serverLevel).levelNavData != null;
        }
        return ClientNavDataManager.getNavDataForPlayer() != null;
    }

    private int colorFromRgb(float red, float green, float blue) {
        return 0x55000000
                | (Math.clamp((int) (red * 255.0f), 0, 255) << 16)
                | (Math.clamp((int) (green * 255.0f), 0, 255) << 8)
                | Math.clamp((int) (blue * 255.0f), 0, 255);
    }

    private int layerColor(int layer) {
        if (layer >= 0) {
            return colorFromRgb(
                    0.125f + layer * 0.125f,
                    1.0f - layer * 0.125f,
                    0.0f
            );
        }
        return colorFromRgb(
                0.125f - layer * 0.125f,
                0.0f,
                1.0f + layer * 0.125f
        );
    }

    /// copied from ae2
    public static int colorFromRatio(double ratio, boolean oneIsGreen) {
        double p = ratio;

        if (!oneIsGreen) {
            p = 1 - p;
        }

        int r = (int) (255d * (Math.clamp(2 - 2 * p, 0, 1)));
        int g = (int) (255d * (Math.clamp(2 * p, 0, 1)));

        return 0xFF000000 + (r << 16) + (g << 8);
    }
}
