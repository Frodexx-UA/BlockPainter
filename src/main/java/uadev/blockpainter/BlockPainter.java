package uadev.blockpainter;

import org.bukkit.plugin.java.JavaPlugin;
import uadev.blockpainter.command.BlockPainterCommand;
import uadev.blockpainter.listener.BrushListener;

import java.util.Objects;

public class BlockPainter extends JavaPlugin {

    private BrushManager brushManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        brushManager = new BrushManager(this);
        messageManager = new MessageManager(this);

        BlockPainterCommand cmd = new BlockPainterCommand(this);
        Objects.requireNonNull(getCommand("blockpainter")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("blockpainter")).setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new BrushListener(this), this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    public BrushManager getBrushManager() {
        return brushManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public void reloadPlugin() {
        reloadConfig();
        brushManager = new BrushManager(this);
        messageManager = new MessageManager(this);
    }
}