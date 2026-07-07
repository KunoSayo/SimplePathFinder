package io.github.kunosayo.simplepathfinder.client.gui;

import io.github.kunosayo.simplepathfinder.data.BlockDistanceKey;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceData;
import io.github.kunosayo.simplepathfinder.network.SyncBlockDistanceConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GUI screen for player block distance configuration.
 * Allows players to configure custom distance costs for blocks and tags.
 * Features real-time validation and suggestions.
 */
public class BlockDistanceConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.simple_path_finder.block_distance.title");

    // UI Components
    private EditBox blockIdEditBox;
    private EditBox distanceEditBox;
    private Button addButton;
    private Button removeButton;
    private Button saveButton;
    private Button cancelButton;
    private Button defaultDistanceButton;

    // List entry buttons (10 entries visible at a time)
    private final List<Button> listEntryButtons = new ArrayList<>();
    private StringWidget validationMessageWidget;
    private StringWidget suggestionWidget;
    private StringWidget pageIndicatorWidget;

    // Current data
    private final PlayerBlockDistanceData currentData;
    private List<BlockDistanceEntry> distanceEntries;
    private int defaultDistance;
    private int selectedIndex = -1;
    private int scrollPosition = 0;

    // Autocomplete suggestions
    private List<String> currentSuggestions = new ArrayList<>();

    // Layout
    private int leftPos;
    private int topPos;

    public BlockDistanceConfigScreen(PlayerBlockDistanceData initialData) {
        super(TITLE);
        this.currentData = initialData;
        this.distanceEntries = new ArrayList<>();
        this.defaultDistance = initialData.defaultDistance();
    }

    @Override
    protected void init() {
        super.init();

        // Initialize entries from current data
        distanceEntries.clear();
        for (Map.Entry<BlockDistanceKey, Integer> entry : currentData.distanceMap().entrySet()) {
            BlockDistanceKey key = entry.getKey();
            String displayId;

            if (key instanceof BlockDistanceKey.BlockIdKey bk) {
                displayId = bk.id().toString();
            } else {
                displayId = "#" + ((BlockDistanceKey.TagKey) key).id().toString();
            }

            // Ensure all entries have proper namespace format
            displayId = normalizeBlockId(displayId);
            distanceEntries.add(new BlockDistanceEntry(displayId, entry.getValue()));
        }
        // Sort by display ID
        distanceEntries.sort((a, b) -> a.displayId.compareToIgnoreCase(b.displayId));

        leftPos = this.width / 2 - 140; // 280 width total
        topPos = this.height / 2 - 110; // 220 height total

        // Title is rendered automatically

        // Block ID / Tag input label
        var idLabel = new StringWidget(
                Component.literal("Block ID / Tag:"), this.font);
        idLabel.setPosition(leftPos, topPos + 8);
        this.addRenderableWidget(idLabel);

        // Block ID / Tag input
        blockIdEditBox = new EditBox(this.font, leftPos + 10, topPos + 20, 180, 20,
                Component.literal("Block ID or Tag"));
        blockIdEditBox.setMaxLength(200);
        blockIdEditBox.setResponder(s -> onInputChanged(s));
        this.addRenderableWidget(blockIdEditBox);

        // Distance input label
        var distLabel = new StringWidget(
                Component.literal("Dist:"), this.font);
        distLabel.setPosition(leftPos + 200, topPos + 8);
        this.addRenderableWidget(distLabel);

        // Distance input
        distanceEditBox = new EditBox(this.font, leftPos + 220, topPos + 20, 50, 20, Component.literal("Distance"));
        distanceEditBox.setValue(String.valueOf(defaultDistance));
        distanceEditBox.setFilter(s -> s.matches("-?\\d*"));
        distanceEditBox.setResponder(s -> updateAddButton());
        this.addRenderableWidget(distanceEditBox);

        // Add button
        addButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.block_distance.add"),
                btn -> addEntry()
        ).bounds(leftPos + 280, topPos + 20, 60, 20).build());

        // Validation message widget
        validationMessageWidget = new StringWidget(Component.empty(), this.font);
        validationMessageWidget.setPosition(leftPos + 80, topPos + 52);
        this.addRenderableWidget(validationMessageWidget);

        // Suggestion widget - shows available suggestions
        suggestionWidget = new StringWidget(Component.empty(), this.font);
        suggestionWidget.setPosition(leftPos + 10, topPos + 68);
        this.addRenderableWidget(suggestionWidget);

        // Page indicator widget
        pageIndicatorWidget = new StringWidget(Component.empty(), this.font);
        pageIndicatorWidget.setPosition(leftPos + 10, topPos + 82);
        this.addRenderableWidget(pageIndicatorWidget);

        // Create list entry buttons (10 entries)
        int listY = topPos + 90;
        int lineHeight = 13;
        for (int i = 0; i < 10; i++) {
            final int index = i;
            int y = listY + i * lineHeight;
            Button entryBtn = this.addRenderableWidget(Button.builder(
                    Component.empty(),
                    btn -> selectEntry(scrollPosition + index)
            ).bounds(leftPos, y, 280, lineHeight - 1).build());
            listEntryButtons.add(entryBtn);
        }

        // Scroll buttons
        int pageSize = 10;
        this.addRenderableWidget(Button.builder(
                Component.literal("◄"),
                btn -> scrollList(-1)
        ).bounds(leftPos + 280, listY, 20, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("►"),
                btn -> scrollList(1)
        ).bounds(leftPos + 280, listY + (pageSize - 1) * lineHeight, 20, 20).build());

        // Remove button
        removeButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.block_distance.remove"),
                btn -> removeSelectedEntry()
        ).bounds(leftPos + 10, listY + pageSize * lineHeight + 10, 80, 20).build());

        // Default distance button
        defaultDistanceButton = this.addRenderableWidget(Button.builder(
                Component.literal("Default: " + defaultDistance),
                btn -> cycleDefaultDistance()
        ).bounds(leftPos + 100, listY + pageSize * lineHeight + 10, 100, 20).build());

        // Save button
        saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.block_distance.save"),
                btn -> saveAndClose()
        ).bounds(leftPos + 10, listY + pageSize * lineHeight + 40, 100, 20).build());

        // Cancel button
        cancelButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.simple_path_finder.block_distance.cancel"),
                btn -> this.onClose()
        ).bounds(leftPos + 130, listY + pageSize * lineHeight + 40, 100, 20).build());

        updateAddButton();
        refreshListDisplay();
    }

    /**
     * Handles input changes with real-time validation and suggestions.
     */
    private void onInputChanged(String input) {
        updateAddButton();
        validateAndAutoComplete(input);
    }

    /**
     * Real-time validation and suggestion generation.
     */
    private void validateAndAutoComplete(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            validationMessageWidget.setMessage(Component.empty());
            suggestionWidget.setMessage(Component.empty());
            currentSuggestions.clear();
            return;
        }

        boolean isTag = trimmed.startsWith("#");
        String idStr = isTag ? trimmed.substring(1) : trimmed;

        Component result;
        currentSuggestions.clear();

        try {
            // Try to parse as identifier
            Identifier id = Identifier.tryParse(idStr);

            // Generate suggestions
            if (isTag) {
                currentSuggestions = generateTagSuggestions(idStr);
            } else {
                currentSuggestions = generateBlockSuggestions(idStr);
            }

            // Check if exact match exists
            if (id != null) {
                if (isTag) {
                    if (checkTagExists(id)) {
                        result = Component.literal("✓ Tag: ").append(Component.literal(id.toString()).withColor(0x55FF55));
                    } else {
                        result = Component.literal("? Tag not found: ").append(Component.literal(id.toString()).withColor(0xFFFF55));
                    }
                } else {
                    if (BuiltInRegistries.BLOCK.containsKey(id)) {
                        var blockRef = BuiltInRegistries.BLOCK.get(id);
                        String blockName = blockRef.isPresent() ?
                                blockRef.get().value().getName().getString() : id.toString();
                        result = Component.literal("✓ Block: ").append(Component.literal(blockName).withColor(0x55FF55));
                    } else {
                        result = Component.literal("? Unknown block: ").append(Component.literal(id.toString()).withColor(0xFFFF55));
                    }
                }
            } else {
                result = Component.literal("✗ Invalid format").withColor(0xFF5555);
            }

            // Show suggestion if available
            if (!currentSuggestions.isEmpty()) {
                String suggestion = currentSuggestions.get(0);
                String prefix = isTag ? "#" : "";
                suggestionWidget.setMessage(Component.literal("Suggestion: ")
                        .append(Component.literal(prefix + suggestion).withColor(0xAAAAAA))
                        .append(Component.literal(" (+").withColor(0x888888))
                        .append(Component.literal(String.valueOf(currentSuggestions.size() - 1)).withColor(0x888888))
                        .append(Component.literal(" more)").withColor(0x888888)));
            } else {
                suggestionWidget.setMessage(Component.empty());
            }
        } catch (Exception e) {
            result = Component.literal("✗ Invalid format").withColor(0xFF5555);
            suggestionWidget.setMessage(Component.empty());
        }

        validationMessageWidget.setMessage(result);
    }

    /**
     * Generates block ID suggestions based on input.
     * Auto-adds "minecraft:" namespace for vanilla blocks when no namespace is specified.
     */
    private List<String> generateBlockSuggestions(String input) {
        String searchLower = input.toLowerCase();

        // If input has no namespace, prioritize minecraft: namespace
        if (!input.contains(":")) {
            List<String> suggestions = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(id -> id.toString())
                    .filter(id -> id.toLowerCase().startsWith(searchLower) ||
                            id.toLowerCase().startsWith("minecraft:" + searchLower))
                    .sorted()
                    .collect(Collectors.toList());

            // Move exact minecraft: match to front if exists
            String minecraftMatch = "minecraft:" + input;
            if (BuiltInRegistries.BLOCK.containsKey(Identifier.tryParse(minecraftMatch))) {
                suggestions.remove(minecraftMatch);
                suggestions.add(0, minecraftMatch);
            }
            return suggestions;
        }

        return BuiltInRegistries.BLOCK.keySet().stream()
                .map(id -> id.toString())
                .filter(id -> id.toLowerCase().startsWith(searchLower))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Generates tag suggestions based on input.
     * Note: This is a simplified version that checks common tag patterns.
     */
    private List<String> generateTagSuggestions(String input) {
        String searchLower = input.toLowerCase();
        List<String> suggestions = new ArrayList<>();

        // Common block tags that users might want to use
        List<String> commonTags = List.of(
                "minecraft:logs", "minecraft:planks", "minecraft:leaves",
                "minecraft:stones", "minecraft:dirt", "minecraft:sand",
                "minecraft:wool", "minecraft:glass", "minecraft:ores",
                "minecraft:rails", "minecraft:stairs", "minecraft:slabs",
                "minecraft:walls", "minecraft:fences", "minecraft:doors"
        );

        // Filter matching tags
        for (String tag : commonTags) {
            if (tag.toLowerCase().contains(searchLower)) {
                suggestions.add(tag);
            }
        }

        // If input has no namespace, also try without minecraft:
        if (!input.contains(":")) {
            for (String tag : commonTags) {
                String withoutNs = tag.substring("minecraft:".length());
                if (withoutNs.toLowerCase().contains(searchLower) && !suggestions.contains(tag)) {
                    suggestions.add(tag);
                }
            }
        }

        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return suggestions;
    }

    private boolean checkTagExists(Identifier tagId) {
        try {
            TagKey<Block> tag = new TagKey<>(Registries.BLOCK, tagId);
            return BuiltInRegistries.BLOCK.stream().anyMatch(block -> {
                var holder = block.builtInRegistryHolder();
                return holder.is(tag);
            });
        } catch (Exception e) {
            return false;
        }
    }

    private void updateAddButton() {
        boolean valid = !blockIdEditBox.getValue().isBlank() && !distanceEditBox.getValue().isBlank();
        addButton.active = valid;
    }

    private void selectEntry(int index) {
        if (index >= 0 && index < distanceEntries.size()) {
            selectedIndex = index;
            BlockDistanceEntry entry = distanceEntries.get(index);
            blockIdEditBox.setValue(entry.displayId);
            distanceEditBox.setValue(String.valueOf(entry.distance));
            refreshListDisplay();
            validateAndAutoComplete(entry.displayId);
        }
    }

    private void scrollList(int direction) {
        if (distanceEntries.isEmpty()) return;

        int pageSize = 10;
        int maxScroll = Math.max(0, distanceEntries.size() - pageSize);

        if (direction > 0) {
            scrollPosition = Math.min(scrollPosition + pageSize, maxScroll);
        } else {
            scrollPosition = Math.max(scrollPosition - pageSize, 0);
        }
        refreshListDisplay();
    }

    private void refreshListDisplay() {
        int pageSize = 10;
        int displayCount = Math.min(pageSize, distanceEntries.size() - scrollPosition);

        for (int i = 0; i < 10; i++) {
            Button btn = listEntryButtons.get(i);
            if (i < displayCount) {
                int entryIndex = scrollPosition + i;
                BlockDistanceEntry entry = distanceEntries.get(entryIndex);
                String text = String.format("%s - Distance: %d", entry.displayId, entry.distance);
                btn.setMessage(Component.literal(text));
                btn.active = true;
                // Highlight selected entry
                if (entryIndex == selectedIndex) {
                    btn.setMessage(Component.literal("→ ").append(Component.literal(text)));
                }
            } else {
                btn.setMessage(Component.empty());
                btn.active = false;
            }
        }

        // Update page indicator
        String pageInfo = String.format("Page %d/%d (%d entries)",
                (scrollPosition / pageSize) + 1,
                Math.max(1, (distanceEntries.size() + pageSize - 1) / pageSize),
                distanceEntries.size());
        pageIndicatorWidget.setMessage(Component.literal(pageInfo));
    }

    private void addEntry() {
        String input = blockIdEditBox.getValue().trim();
        int distance;

        try {
            distance = Integer.parseInt(distanceEditBox.getValue());
        } catch (NumberFormatException e) {
            distance = defaultDistance;
        }

        // Normalize the input to ensure proper namespace
        String normalizedId = normalizeBlockId(input);

        // Validate before adding
        if (!isValidBlockId(normalizedId)) {
            // Show error and don't add
            validateAndAutoComplete(input);
            return;
        }

        // Check if already exists and update (use normalized ID for comparison)
        boolean found = false;
        for (int i = 0; i < distanceEntries.size(); i++) {
            BlockDistanceEntry entry = distanceEntries.get(i);
            // Compare normalized IDs to ensure "dirt" and "minecraft:dirt" are treated as same
            if (entry.displayId.equals(normalizedId)) {
                entry.distance = distance;
                found = true;
                selectedIndex = i;
                break;
            }
        }

        if (!found) {
            distanceEntries.add(new BlockDistanceEntry(normalizedId, distance));
            distanceEntries.sort((a, b) -> a.displayId.compareToIgnoreCase(b.displayId));
            // Find new index
            for (int i = 0; i < distanceEntries.size(); i++) {
                if (distanceEntries.get(i).displayId.equals(normalizedId)) {
                    selectedIndex = i;
                    // Ensure the new entry is visible in the scroll view
                    int pageSize = 10;
                    if (selectedIndex < scrollPosition) {
                        scrollPosition = Math.max(0, selectedIndex);
                    } else if (selectedIndex >= scrollPosition + pageSize) {
                        scrollPosition = Math.min(distanceEntries.size() - pageSize, selectedIndex - pageSize + 1);
                        scrollPosition = Math.max(0, scrollPosition);
                    }
                    break;
                }
            }
        }

        blockIdEditBox.setValue("");
        distanceEditBox.setValue(String.valueOf(defaultDistance));
        validationMessageWidget.setMessage(Component.empty());
        suggestionWidget.setMessage(Component.empty());
        currentSuggestions.clear();
        refreshListDisplay();
    }

    /**
     * Normalizes a block ID to ensure it has a namespace.
     * If no namespace is present, adds "minecraft:".
     * This ensures "dirt" and "minecraft:dirt" are treated as the same thing.
     */
    private String normalizeBlockId(String input) {
        if (input.startsWith("#")) {
            // Tag - format the tag ID
            String tagId = input.substring(1);
            if (!tagId.contains(":")) {
                return "#" + "minecraft:" + tagId;
            }
            return input;
        } else {
            // Block - ensure namespace
            if (!input.contains(":")) {
                return "minecraft:" + input;
            }
            return input;
        }
    }

    /**
     * Checks if a block ID is valid (exists).
     */
    private boolean isValidBlockId(String input) {
        boolean isTag = input.startsWith("#");
        String idStr = isTag ? input.substring(1) : input;

        try {
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) return false;

            if (isTag) {
                return checkTagExists(id);
            } else {
                return BuiltInRegistries.BLOCK.containsKey(id);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void removeSelectedEntry() {
        String input = blockIdEditBox.getValue().trim();
        if (input.isEmpty()) return;

        // Normalize input for comparison
        String normalizedId = normalizeBlockId(input);

        for (int i = 0; i < distanceEntries.size(); i++) {
            // Compare normalized IDs
            if (distanceEntries.get(i).displayId.equals(normalizedId)) {
                distanceEntries.remove(i);
                selectedIndex = -1;
                // Adjust scroll position if needed
                int pageSize = 10;
                if (distanceEntries.isEmpty()) {
                    scrollPosition = 0;
                } else if (scrollPosition >= distanceEntries.size()) {
                    scrollPosition = Math.max(0, distanceEntries.size() - pageSize);
                }
                break;
            }
        }

        blockIdEditBox.setValue("");
        distanceEditBox.setValue(String.valueOf(defaultDistance));
        validationMessageWidget.setMessage(Component.empty());
        suggestionWidget.setMessage(Component.empty());
        currentSuggestions.clear();
        refreshListDisplay();
    }

    private void cycleDefaultDistance() {
        defaultDistance = switch (defaultDistance) {
            case 0 -> 1;
            case 1 -> 10;
            case 10 -> 50;
            default -> 0;
        };
        defaultDistanceButton.setMessage(Component.literal("Default: " + defaultDistance));
        distanceEditBox.setValue(String.valueOf(defaultDistance));
    }

    private void saveAndClose() {
        Map<BlockDistanceKey, Integer> newMap = new HashMap<>();

        for (BlockDistanceEntry entry : distanceEntries) {
            Identifier id;
            try {
                String idStr = entry.displayId.startsWith("#") ?
                        entry.displayId.substring(1) : entry.displayId;
                id = Identifier.tryParse(idStr);
            } catch (Exception e) {
                continue;
            }

            if (id == null) continue;

            BlockDistanceKey key = entry.displayId.startsWith("#") ?
                    BlockDistanceKey.tag(id) :
                    BlockDistanceKey.block(id);
            newMap.put(key, entry.distance);
        }

        PlayerBlockDistanceData newData = new PlayerBlockDistanceData(newMap, defaultDistance);

        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new SyncBlockDistanceConfigPacket(newData));
        }

        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Handles keyboard input for Tab autocomplete.
     * Call this from a keyboard event handler.
     */
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Tab key is 258 in GLFW
        if (keyCode == 258 && !currentSuggestions.isEmpty()) {
            String suggestion = currentSuggestions.get(0);
            boolean isTag = blockIdEditBox.getValue().trim().startsWith("#");
            String prefix = isTag ? "#" : "";
            blockIdEditBox.setValue(prefix + suggestion);
            blockIdEditBox.moveCursorToEnd(true);
            validateAndAutoComplete(prefix + suggestion);
            return true;
        }
        return false;
    }

    /**
     * Entry in the distance list.
     */
    public static class BlockDistanceEntry {
        public String displayId;
        public int distance;

        public BlockDistanceEntry(String displayId, int distance) {
            this.displayId = displayId;
            this.distance = distance;
        }

        public String getDisplayText() {
            return String.format("%-50s Distance: %d", displayId, distance);
        }
    }
}
