package uadev.blockpainter;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrushManager {

    private final BlockPainter plugin;
    public final NamespacedKey COLOR_KEY;
    public final NamespacedKey INK_KEY;

    private Material brushMaterial;
    private String brushName;
    private List<String> loreHeaderLines;
    private List<String> colorsTitleLines;
    private List<String> loreFooter;
    private String inkFormat;

    private final Map<String, String> colors = new LinkedHashMap<>();

    private static final String[] KNOWN_SUFFIXES = {
            "_STAINED_GLASS_PANE",
            "_STAINED_GLASS",
            "_WOOL",
            "_CONCRETE",
            "_CONCRETE_POWDER",
            "_GLAZED_TERRACOTTA",
            "_TERRACOTTA",
            "_CARPET",
            "_BED",
            "_CANDLE",
            "_BANNER",
            "_WALL_BANNER",
    };

    private static final Map<String, Material> DYE_MAP = new LinkedHashMap<>();
    static {
        DYE_MAP.put("WHITE",      Material.WHITE_DYE);
        DYE_MAP.put("ORANGE",     Material.ORANGE_DYE);
        DYE_MAP.put("MAGENTA",    Material.MAGENTA_DYE);
        DYE_MAP.put("LIGHT_BLUE", Material.LIGHT_BLUE_DYE);
        DYE_MAP.put("YELLOW",     Material.YELLOW_DYE);
        DYE_MAP.put("LIME",       Material.LIME_DYE);
        DYE_MAP.put("PINK",       Material.PINK_DYE);
        DYE_MAP.put("GRAY",       Material.GRAY_DYE);
        DYE_MAP.put("LIGHT_GRAY", Material.LIGHT_GRAY_DYE);
        DYE_MAP.put("CYAN",       Material.CYAN_DYE);
        DYE_MAP.put("PURPLE",     Material.PURPLE_DYE);
        DYE_MAP.put("BLUE",       Material.BLUE_DYE);
        DYE_MAP.put("BROWN",      Material.BROWN_DYE);
        DYE_MAP.put("GREEN",      Material.GREEN_DYE);
        DYE_MAP.put("RED",        Material.RED_DYE);
        DYE_MAP.put("BLACK",      Material.BLACK_DYE);
    }

    public BrushManager(BlockPainter plugin) {
        this.plugin = plugin;
        this.COLOR_KEY = new NamespacedKey(plugin, "brush_color");
        this.INK_KEY   = new NamespacedKey(plugin, "brush_ink");
        loadConfig();
    }

    private void loadConfig() {
        brushMaterial = Material.matchMaterial(plugin.getConfig().getString("brush.material", "BRUSH"));
        if (brushMaterial == null) brushMaterial = Material.BRUSH;

        brushName        = c(plugin.getConfig().getString("brush.name", "&6&lКольорозмінювач"));
        inkFormat        = plugin.getConfig().getString("brush.lore.ink",  "&7Чорнила: %ink_color%%ink%&7/&f%max_ink%");
        loreHeaderLines  = expandLines(plugin.getConfig().getStringList("brush.lore.header"));
        colorsTitleLines = expandLines(plugin.getConfig().getStringList("brush.lore.colors-title"));

        loreFooter = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("brush.lore.footer")) {
            loreFooter.addAll(expandLine(line));
        }

        colors.clear();
        for (String entry : plugin.getConfig().getStringList("colors")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                colors.put(parts[0].trim(), parts[1]);
            }
        }
    }

    private List<String> expandLines(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String line : input) result.addAll(expandLine(line));
        return result;
    }

    private List<String> expandLine(String line) {
        List<String> result = new ArrayList<>();
        for (String part : line.split("\\\\n", -1)) result.add(c(part));
        return result;
    }

    public ItemStack createBrush(int amount) {
        ItemStack item = new ItemStack(brushMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(brushName);

        String firstColor = colors.isEmpty() ? "WHITE" : colors.keySet().iterator().next();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(COLOR_KEY, PersistentDataType.STRING, firstColor);
        pdc.set(INK_KEY,   PersistentDataType.INTEGER, getMaxInk());

        meta.setLore(buildLore(firstColor, getMaxInk()));
        item.setItemMeta(meta);
        return item;
    }

    public List<String> buildLore(String selectedColor, int ink) {
        List<String> lore = new ArrayList<>(loreHeaderLines);

        if (ink >= 0) {
            int maxInk = plugin.getConfig().getInt("ink-refill.max-ink", 640);
            String inkColor = ink == 0 ? "&c" : ink <= maxInk / 4 ? "&e" : "&f";
            String line = inkFormat
                    .replace("%ink_color%", inkColor)
                    .replace("%ink%",       String.valueOf(ink))
                    .replace("%max_ink%",   String.valueOf(maxInk));
            lore.add(c(line));
        }

        lore.addAll(colorsTitleLines);

        List<Map.Entry<String, String>> entries = new ArrayList<>(colors.entrySet());
        for (int i = 0; i < entries.size(); i += 2) {
            Map.Entry<String, String> left = entries.get(i);
            String leftFormatted = formatColorEntry(left.getKey(), left.getValue(), selectedColor);

            if (i + 1 < entries.size()) {
                Map.Entry<String, String> right = entries.get(i + 1);
                String rightFormatted = formatColorEntry(right.getKey(), right.getValue(), selectedColor);
                lore.add(leftFormatted + rightFormatted);
            } else {
                lore.add(leftFormatted);
            }
        }

        lore.addAll(loreFooter);
        return lore;
    }

    private String formatColorEntry(String colorKey, String colorName, String selectedColor) {
        if (colorKey.equals(selectedColor)) {
            return getColorCode(colorKey) + colorName;
        }
        return c("&8") + colorName;
    }

    public String getNextColor(String currentColor) {
        List<String> keys = new ArrayList<>(colors.keySet());
        int idx = keys.indexOf(currentColor);
        if (idx < 0) return keys.isEmpty() ? "WHITE" : keys.getFirst();
        return keys.get((idx + 1) % keys.size());
    }

    public Map<String, String> getColors() {
        return colors;
    }

    public boolean isBrush(ItemStack item) {
        if (item == null || item.getType() != brushMaterial) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(COLOR_KEY, PersistentDataType.STRING);
    }

    public boolean isPaintable(Material material) {
        String name = material.name();
        for (String pattern : plugin.getConfig().getStringList("paintable-blocks")) {
            if (name.matches(pattern.replace("*", ".*"))) return false;
        }
        return true;
    }

    public String getSuffix(Material material) {
        String name = material.name();
        for (String suffix : KNOWN_SUFFIXES) {
            if (name.endsWith(suffix)) return suffix;
        }
        return null;
    }

    public Material getColoredBlock(Material original, String colorKey) {
        String suffix = getSuffix(original);
        if (suffix == null) return original;
        Material result = Material.matchMaterial(colorKey + suffix);
        return result != null ? result : original;
    }

    public String getColorForDye(Material dye) {
        for (Map.Entry<String, Material> entry : DYE_MAP.entrySet()) {
            if (entry.getValue() == dye) return entry.getKey();
        }
        return null;
    }


    public int getInkPerDye() {
        return plugin.getConfig().getInt("ink-refill.ink-per-dye", 1);
    }

    public int getMaxInk() {
        return plugin.getConfig().getInt("ink-refill.max-ink", 640);
    }

    public String getColorCode(String colorKey) {
        return switch (colorKey) {
            case "ORANGE"     -> hex("#F9801D");
            case "MAGENTA"    -> hex("#C74EBD");
            case "LIGHT_BLUE" -> hex("#3AB3DA");
            case "YELLOW"     -> hex("#FED83D");
            case "LIME"       -> hex("#80C71F");
            case "PINK"       -> hex("#F38BAA");
            case "GRAY"       -> hex("#474F52");
            case "LIGHT_GRAY" -> hex("#9D9D97");
            case "CYAN"       -> hex("#169C9C");
            case "PURPLE"     -> hex("#8932B8");
            case "BLUE"       -> hex("#3C44AA");
            case "BROWN"      -> hex("#835432");
            case "GREEN"      -> hex("#5E7C16");
            case "RED"        -> hex("#B02E26");
            case "BLACK"      -> hex("#1D1D21");
            default           -> hex("#F9FFFE");
        };
    }

    private String hex(String color) {
        return ChatColor.of(color).toString();
    }

    private String c(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}