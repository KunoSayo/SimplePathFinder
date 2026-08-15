package io.github.kunosayo.simplepathfinder.client.gui;

import io.github.kunosayo.simplepathfinder.data.NavigationModeData;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.init.ModAttachments;
import io.github.kunosayo.simplepathfinder.item.NavigationItem;
import io.github.kunosayo.simplepathfinder.item.NavigationMode;
import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
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
    private Button blockDistanceConfigButton;
    private EditBox layerEditBox;
    private StringWidget layerLabel;
    private Button saveButton;
    private Button cancelButton;

    // Link type buttons (shown only in ADD_LINK mode)
    private Button linkTypeNormalButton;
    private Button linkTypeTeleportButton;
    private Button linkTypeVehicleButton;

    // Current settings
    private NavigationMode currentMode;
    private int currentLayer;
    private NavLinkType currentLinkType;

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

        // Get current link type
        this.currentLinkType = NavigationItem.getLinkType(navStack);
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = this.width / 2 - 90; // Center horizontally (180 width)
        int topPos = this.height / 2 - 90; // Center vertically (160 height)

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

        // Link type buttons (third row) - only visible in ADD_LINK mode
        buttonY = topPos + 70;
        linkTypeNormalButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.link_type.normal"),
                btn -> setLinkType(NavLinkType.NORMAL)
        ).bounds(leftPos + 10, buttonY, 50, 20).build());

        linkTypeTeleportButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.link_type.teleport"),
                btn -> setLinkType(NavLinkType.TELEPORT)
        ).bounds(leftPos + 65, buttonY, 50, 20).build());

        linkTypeVehicleButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.link_type.vehicle"),
                btn -> setLinkType(NavLinkType.VEHICLE)
        ).bounds(leftPos + 120, buttonY, 50, 20).build());

        // Layer input (fourth row)
        buttonY = topPos + 100;
        // Add label for layer input
        layerLabel = new StringWidget(
                Component.translatable("gui.simple_path_finder.navigation.layer"), this.font);
        layerLabel.setPosition(leftPos + 10, buttonY + 3);
        this.addRenderableWidget(layerLabel);
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
        buttonY = topPos + 130;
        saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.save"),
                btn -> saveAndClose()
        ).bounds(leftPos + 10, buttonY, 50, 20).build());

        // Block distance config button
        blockDistanceConfigButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.block_distance_config"),
                btn -> openBlockDistanceConfig()
        ).bounds(leftPos + 70, buttonY, 110, 20).build());

        cancelButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.navigation.cancel"),
                btn -> this.onClose()
        ).bounds(leftPos + 10, buttonY + 25, 170, 20).build());

        updateButtonStates();
    }

    private void setMode(NavigationMode mode) {
        this.currentMode = mode;
        updateButtonStates();
    }

    private void setLinkType(NavLinkType type) {
        this.currentLinkType = type;
        updateButtonStates();
    }

    private void updateButtonStates() {
        // Update mode button states
        modeDefaultButton.active = currentMode != NavigationMode.DEFAULT;
        modeAddNavButton.active = currentMode != NavigationMode.ADD_NAV;
        modeRemoveNavButton.active = currentMode != NavigationMode.REMOVE_NAV;
        modeAddLinkButton.active = currentMode != NavigationMode.ADD_LINK;

        // Update link type button visibility and states
        boolean isAddLinkMode = currentMode == NavigationMode.ADD_LINK;
        linkTypeNormalButton.visible = isAddLinkMode;
        linkTypeTeleportButton.visible = isAddLinkMode;
        linkTypeVehicleButton.visible = isAddLinkMode;

        linkTypeNormalButton.active = currentLinkType != NavLinkType.NORMAL;
        linkTypeTeleportButton.active = currentLinkType != NavLinkType.TELEPORT;
        linkTypeVehicleButton.active = currentLinkType != NavLinkType.VEHICLE;
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
        // Update link type
        NavigationItem.setLinkType(currentStack, currentLinkType);

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
     * Open the block distance configuration screen.
     */
    private void openBlockDistanceConfig() {
        // Use the synced player block distance data from the server
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PlayerBlockDistanceData data = player.getData(ModAttachments.PLAYER_BLOCK_DISTANCE).getData();
        Minecraft.getInstance().setScreen(new BlockDistanceConfigScreen(data));
    }

    /**
     * Open the navigation configuration screen on client
     */
    public static void open(ItemStack navStack, InteractionHand hand) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new NavigationScreen(navStack, hand));
    }
}
