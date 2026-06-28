package io.github.kunosayo.simplepathfinder.client.gui;

import io.github.kunosayo.simplepathfinder.data.NavBrushData;
import io.github.kunosayo.simplepathfinder.item.NavBrushItem;
import io.github.kunosayo.simplepathfinder.item.NavBrushMode;
import io.github.kunosayo.simplepathfinder.item.NavBrushOperation;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * GUI screen for navigation brush configuration.
 * Allows players to select brush mode, operation, and weight value.
 */
public class NavBrushScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.simple_path_finder.nav_brush.title");

    // Buttons
    private Button modeAllEdgesButton;
    private Button modeSingleEdgeButton;
    private Button operationDeleteButton;
    private Button operationAddButton;
    private Button operationAdjustWeightButton;
    private EditBox weightEditBox;
    private Button saveButton;
    private Button cancelButton;

    // Current settings
    private NavBrushMode currentMode;
    private NavBrushOperation currentOperation;
    private int currentWeight;

    // Item reference
    private final ItemStack brushStack;
    private final InteractionHand hand;

    public NavBrushScreen(ItemStack brushStack, InteractionHand hand) {
        super(TITLE);
        this.brushStack = brushStack;
        this.hand = hand;

        // Get current brush data
        NavBrushData brushData = NavBrushItem.getBrushData(brushStack);
        this.currentMode = brushData.mode();
        this.currentOperation = brushData.operation();
        this.currentWeight = brushData.weightValue();
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = this.width / 2 - 90; // Center horizontally (180 width)
        int topPos = this.height / 2 - 70; // Center vertically (140 height)

        // Mode buttons (top row)
        int buttonY = topPos + 10;
        modeAllEdgesButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.mode.all_edges"),
                btn -> setMode(NavBrushMode.ALL_EDGES)
        ).bounds(leftPos + 10, buttonY, 80, 20).build());

        modeSingleEdgeButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.mode.single_edge"),
                btn -> setMode(NavBrushMode.SINGLE_EDGE)
        ).bounds(leftPos + 90, buttonY, 80, 20).build());

        // Operation buttons (middle row)
        buttonY = topPos + 40;
        operationDeleteButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.operation.delete"),
                btn -> setOperation(NavBrushOperation.DELETE)
        ).bounds(leftPos + 10, buttonY, 50, 20).build());

        operationAddButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.operation.add"),
                btn -> setOperation(NavBrushOperation.ADD)
        ).bounds(leftPos + 65, buttonY, 50, 20).build());

        operationAdjustWeightButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.operation.adjust_weight"),
                btn -> setOperation(NavBrushOperation.ADJUST_WEIGHT)
        ).bounds(leftPos + 120, buttonY, 50, 20).build());

        // Weight input (bottom row)
        buttonY = topPos + 70;
        weightEditBox = new EditBox(
                this.font,
                leftPos + 65,
                buttonY,
                70,
                20,
                Component.literal("Weight")
        );
        weightEditBox.setValue(String.valueOf(currentWeight));
        weightEditBox.setResponder(value -> {
            try {
                int weight = Integer.parseInt(value);
                currentWeight = Mth.clamp(weight, -1, 10000);
            } catch (NumberFormatException e) {
                // Keep current value if invalid
            }
        });
        weightEditBox.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(weightEditBox);

        // Save and Cancel buttons
        buttonY = topPos + 100;
        saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.save"),
                btn -> saveAndClose()
        ).bounds(leftPos + 40, buttonY, 50, 20).build());

        cancelButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.nav_brush.cancel"),
                btn -> this.onClose()
        ).bounds(leftPos + 100, buttonY, 50, 20).build());

        updateButtonStates();
    }

    private void setMode(NavBrushMode mode) {
        this.currentMode = mode;
        updateButtonStates();
    }

    private void setOperation(NavBrushOperation operation) {
        this.currentOperation = operation;
        updateButtonStates();
    }

    private void updateButtonStates() {
        // Update mode button states
        modeAllEdgesButton.active = currentMode != NavBrushMode.ALL_EDGES;
        modeSingleEdgeButton.active = currentMode != NavBrushMode.SINGLE_EDGE;

        // Update operation button states
        operationDeleteButton.active = currentOperation != NavBrushOperation.DELETE;
        operationAddButton.active = currentOperation != NavBrushOperation.ADD;
        operationAdjustWeightButton.active = currentOperation != NavBrushOperation.ADJUST_WEIGHT;

        // Update weight edit box visibility
        weightEditBox.active = currentOperation == NavBrushOperation.ADJUST_WEIGHT;
        weightEditBox.setVisible(currentOperation == NavBrushOperation.ADJUST_WEIGHT);
    }

    private void saveAndClose() {
        // Parse weight value
        try {
            currentWeight = Integer.parseInt(weightEditBox.getValue());
            currentWeight = Mth.clamp(currentWeight, -1, 10000);
        } catch (NumberFormatException e) {
            currentWeight = -1;
        }

        // Validate item still exists and is the correct type
        Player player = this.minecraft.player;
        if (player == null) {
            this.onClose();
            return;
        }

        ItemStack currentStack = player.getItemInHand(hand);
        if (currentStack.isEmpty() || !(currentStack.getItem() instanceof NavBrushItem)) {
            // Item is no longer in hand, close screen
            this.onClose();
            return;
        }

        // Update item data and sync with server
        NavBrushItem.setBrushDataSync(currentStack, hand, new NavBrushData(
                currentMode, currentOperation, currentWeight
        ));

        this.onClose();
    }

    @Override
    public void onClose() {
        // Validate item still exists before closing
        Player player = this.minecraft.player;
        if (player != null && hand != null) {
            ItemStack currentStack = player.getItemInHand(hand);
            if (currentStack.isEmpty() || !(currentStack.getItem() instanceof NavBrushItem)) {
                // Item is no longer in hand, just close
            }
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }

    /**
     * Open the nav brush configuration screen on client
     */
    public static void open(ItemStack brushStack, InteractionHand hand) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new NavBrushScreen(brushStack, hand));
    }
}
