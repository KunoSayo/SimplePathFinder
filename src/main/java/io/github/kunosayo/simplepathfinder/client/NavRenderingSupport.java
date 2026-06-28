package io.github.kunosayo.simplepathfinder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.kunosayo.simplepathfinder.SimplePathFinder;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
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

        NavResult clientNavResult = SimplePathFinder.clientNavResult;
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
        Iterator<IRenderElement> iterator = elements.iterator();
        while (iterator.hasNext()) {
            IRenderElement element = iterator.next();
            if (element instanceof Line line) {
                if (!tryMergeLine(line)) {
                    lineElements.add(line);
                }
                iterator.remove();
            }
        }
        linesDirty = false;
    }

    private boolean tryMergeLine(Line line) {
        if (lineElements.isEmpty()) {
            return false;
        }
        int lastIndex = lineElements.size() - 1;
        Line last = lineElements.get(lastIndex);
        if (last.thickness != line.thickness || !samePosition(last.end, line.start)) {
            return false;
        }
        if (!sameDirection(last.start, last.end, line.start, line.end)) {
            return false;
        }
        lineElements.set(lastIndex, new Line(last.start, line.end, last.thickness, last.startColor, line.endColor));
        return true;
    }

    private boolean samePosition(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) < EPSILON * EPSILON;
    }

    private boolean sameDirection(Vec3 firstStart, Vec3 firstEnd, Vec3 secondStart, Vec3 secondEnd) {
        Vec3 first = firstEnd.subtract(firstStart);
        Vec3 second = secondEnd.subtract(secondStart);
        return first.dot(second) > 0.0
                && Math.abs(first.y * second.z - first.z * second.y) < EPSILON
                && Math.abs(first.z * second.x - first.x * second.z) < EPSILON
                && Math.abs(first.x * second.y - first.y * second.x) < EPSILON;
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

    private interface IRenderElement {
        void render(PoseStack poseStack, SubmitNodeCollector collector);
    }

    private record Line(
            Vec3 start,
            Vec3 end,
            float length,
            int thickness,
            int startColor,
            int endColor
    ) implements IRenderElement {

        public Line(Vec3 start, Vec3 end, int thickness, int startColor, int endColor) {
            this(start, end, (float) start.distanceTo(end), thickness, startColor, endColor);
        }

        @Override
        public void render(PoseStack poseStack, SubmitNodeCollector collector) {
            collector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.lines(),
                    (pose, vertex) -> {
                        float dx = (float) (this.start().x - this.end().x);
                        float dy = (float) (this.start().y - this.end().y);
                        float dz = (float) (this.start().z - this.end().z);
                        vertex.addVertex(
                                        pose.pose(),
                                        (float) (this.start().x),
                                        (float) (this.start().y),
                                        (float) (this.start().z)
                                )
                                .setColor(startColor)
                                .setLineWidth(this.thickness)
                                .setNormal(pose, dx /= this.length(), dy /= this.length(), dz /= this.length());
                        vertex.addVertex(
                                        pose.pose(),
                                        (float) (this.end().x),
                                        (float) (this.end().y),
                                        (float) (this.end().z)
                                )
                                .setLineWidth(this.thickness)
                                .setColor(endColor)
                                .setNormal(pose, dx, dy, dz);
                    }
            );
        }
    }

    private record FilledBox(
            Vec3 center,
            float size,
            int color
    ) implements IRenderElement {
        @Override
        public void render(PoseStack poseStack, SubmitNodeCollector collector) {
            float radius = size * 0.5f;
            float minX = (float) center.x - radius;
            float minY = (float) center.y - radius;
            float minZ = (float) center.z - radius;
            float maxX = (float) center.x + radius;
            float maxY = (float) center.y + radius;
            float maxZ = (float) center.z + radius;

            collector.submitCustomGeometry(
                    poseStack,
                    RenderTypes.debugFilledBox(),
                    (pose, vertex) -> {
                        quad(vertex, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ);
                        quad(vertex, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
                        quad(vertex, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
                        quad(vertex, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
                        quad(vertex, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
                        quad(vertex, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
                    }
            );
        }

        private void quad(
                VertexConsumer vertex,
                PoseStack.Pose pose,
                float x1,
                float y1,
                float z1,
                float x2,
                float y2,
                float z2,
                float x3,
                float y3,
                float z3,
                float x4,
                float y4,
                float z4
        ) {
            vertex.addVertex(pose.pose(), x1, y1, z1).setColor(color);
            vertex.addVertex(pose.pose(), x2, y2, z2).setColor(color);
            vertex.addVertex(pose.pose(), x3, y3, z3).setColor(color);
            vertex.addVertex(pose.pose(), x4, y4, z4).setColor(color);
        }
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
