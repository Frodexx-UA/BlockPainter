package uadev.blockpainter;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageManager {

    private final BlockPainter plugin;

    public MessageManager(BlockPainter plugin) {
        this.plugin = plugin;
    }

    public String get(String key) {
        String raw = plugin.getConfig().getString("messages." + key, "&cПовідомлення не знайдено: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String get(String key, String... placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, "&cПовідомлення не знайдено: " + key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace(placeholders[i], placeholders[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }
}