package io.github.kunosayo.simplepathfinder.client.gui;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * GUI screen for navigation point configuration.
 * Allows players to select navigation mode and layer value.
 */
public class NavigationScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.simple_path_finder.navigation.title");

    // Buttons
    private Button modeDefaultButton;
    private Button modeAddNavButton;
    private Button modeRemoveNavButton;
    private Button modeAddLinkButton;
    private EditBox layerEditBox;
    private Button saveButton;
    private Button cancelButton;

    // Current settings
    private NavigationMode currentMode;
    private int currentLayer;

    // Item reference
    private final ItemStack navStack;
    private final InteractionHand hand;

    public NavigationScreen(ItemStack navStack, InteractionHand hand) {
        super(TITLE);
        this.navStack = navStack;
        this.hand = hand;

        // Get current navigation mode data
        NavigationModeData modeData = NavigationItem.getNavigationModeData(navStack);
        if (modeData != null) {
            this.currentMode = modeData.mode();
            this.currentLayer = modeData.layer();
        } else {
            this.currentMode = NavigationMode.DEFAULT;
            this.currentLayer = 0;
        }
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = this.width / 2 - 90; // Center horizontally (180 width)
        int topPos = this.height / 2 - 70; // Center vertically (140 height)

        // Mode buttons (top row)
        int buttonY = topPos + 10;
        modeDefaultButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.mode.default"),
                btn -> setMode(NavigationMode.DEFAULT)
        ).bounds(leftPos + 10, buttonY, 80, 20).build());

        modeAddNavButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.mode.add_nav"),
                btn -> setMode(NavigationMode.ADD_NAV)
        ).bounds(leftPos + 90, buttonY, 80, 20).build());

        // Remove nav button (second row)
        buttonY = topPos + 40;
        modeRemoveNavButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.mode.remove_nav"),
                btn -> setMode(NavigationMode.REMOVE_NAV)
        ).bounds(leftPos + 10, buttonY, 80, 20).build());

        // Add link button (second row, right side)
        modeAddLinkButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.mode.add_link"),
                btn -> setMode(NavigationMode.ADD_LINK)
        ).bounds(leftPos + 90, buttonY, 80, 20).build());

        // Layer input (third row)
        buttonY = topPos + 70;
        layerEditBox = new EditBox(
                this.font,
                leftPos + 65,
                buttonY,
                70,
                20,
                Component.literal("Layer")
        );
        layerEditBox.setValue(String.valueOf(currentLayer));
        layerEditBox.setResponder(value -> {
            try {
                int layer = Integer.parseInt(value);
                currentLayer = net.minecraft.util.Mth.clamp(layer, -128, 127);
            } catch (NumberFormatException e) {
                // Keep current value if invalid
            }
        });
        layerEditBox.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(layerEditBox);

        // Save and Cancel buttons
        buttonY = topPos + 100;
        saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.save"),
                btn -> saveAndClose()
        ).bounds(leftPos + 40, buttonY, 50, 20).build());

        cancelButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.cancel"),
                btn -> this.onClose()
        ).bounds(leftPos + 100, buttonY, 50, 20).build());

        updateButtonStates();
    }

    private void setMode(NavigationMode mode) {
        this.currentMode = mode;
        updateButtonStates();
    }

    private void updateButtonStates() {
        // Update mode button states
        modeDefaultButton.active = currentMode != NavigationMode.DEFAULT;
        modeAddNavButton.active = currentMode != NavigationMode.ADD_NAV;
        modeRemoveNavButton.active = currentMode != NavigationMode.REMOVE_NAV;
        modeAddLinkButton.active = currentMode != NavigationMode.ADD_LINK;
    }

    private void saveAndClose() {
        // Parse layer value
        try {
            currentLayer = Integer.parseInt(layerEditBox.getValue());
            currentLayer = net.minecraft.util.Mth.clamp(currentLayer, -128, 127);
        } catch (NumberFormatException e) {
            currentLayer = 0;
        }

        // Validate item still exists and is the correct type
        Player player = this.minecraft.player;
        if (player == null) {
            this.onClose();
            return;
        }

        ItemStack currentStack = player.getItemInHand(hand);
        if (currentStack.isEmpty() || !(currentStack.getItem() instanceof NavigationItem)) {
            // Item is no longer in hand, close screen
            this.onClose();
            return;
        }

        // Update item data and sync with server
        NavigationItem.setNavigationModeDataSync(currentStack, hand, currentMode, (byte) currentLayer);

        this.onClose();
    }

    @Override
    public void onClose() {
        // Validate item still exists before closing
        Player player = this.minecraft.player;
        if (player != null && hand != null) {
            ItemStack currentStack = player.getItemInHand(hand);
            if (currentStack.isEmpty() || !(currentStack.getItem() instanceof NavigationItem)) {
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
     * Open the navigation configuration screen on client
     */
    public static void open(ItemStack navStack, InteractionHand hand) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new NavigationScreen(navStack, hand));
    }
}
