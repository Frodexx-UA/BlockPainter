package uadev.blockpainter.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import uadev.blockpainter.BlockPainter;
import uadev.blockpainter.MessageManager;

import java.util.ArrayList;
import java.util.List;

public class BlockPainterCommand implements CommandExecutor, TabCompleter {

    private final BlockPainter plugin;

    public BlockPainterCommand(BlockPainter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        MessageManager msg = plugin.getMessageManager();

        if (!sender.hasPermission("blockpainter.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give"   -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            default       -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (!sender.hasPermission("blockpainter.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("give", "reload"), args[0]);
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return filter(players, args[1]);
            }
            if (args.length == 3) return filter(List.of("1", "8", "16", "32", "64"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) result.add(s);
        }
        return result;
    }

    private void handleGive(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();

        if (args.length < 2) {
            msg.send(sender, "give-usage");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found", "%player%", args[1]);
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 64) {
                    msg.send(sender, "invalid-amount-range");
                    return;
                }
            } catch (NumberFormatException e) {
                msg.send(sender, "invalid-amount-number", "%input%", args[2]);
                return;
            }
        }

        ItemStack brush = plugin.getBrushManager().createBrush(amount);
        target.getInventory().addItem(brush);

        msg.send(target, "give-received", "%amount%", String.valueOf(amount));
        if (!sender.equals(target)) {
            msg.send(sender, "give-sent", "%amount%", String.valueOf(amount), "%player%", target.getName());
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        plugin.getMessageManager().send(sender, "reloaded");
    }

    private void sendHelp(CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();
        msg.send(sender, "help-header");
        msg.send(sender, "help-give");
        msg.send(sender, "help-reload");
    }
}