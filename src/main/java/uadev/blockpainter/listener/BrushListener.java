package uadev.blockpainter.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Candle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import uadev.blockpainter.BlockPainter;
import uadev.blockpainter.BrushManager;
import uadev.blockpainter.MessageManager;

import java.util.Objects;

public class BrushListener implements Listener {

    private final BlockPainter plugin;

    public BrushListener(BlockPainter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        BrushManager brushManager = plugin.getBrushManager();
        MessageManager msg = plugin.getMessageManager();

        if (!brushManager.isBrush(item)) return;

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleInkRefill(event, player, item, brushManager, msg);
            return;
        }

        boolean isRightClick = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
        if (!isRightClick) return;

        event.setCancelled(true);

        ItemMeta meta = Objects.requireNonNull(item).getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String currentColor = pdc.getOrDefault(brushManager.COLOR_KEY, PersistentDataType.STRING, "WHITE");
        int ink = pdc.getOrDefault(brushManager.INK_KEY, PersistentDataType.INTEGER, brushManager.getMaxInk());

        if (player.isSneaking()) {
            String nextColor = brushManager.getNextColor(currentColor);
            pdc.set(brushManager.COLOR_KEY, PersistentDataType.STRING, nextColor);
            meta.setLore(brushManager.buildLore(nextColor, ink));
            item.setItemMeta(meta);

            String colorName = brushManager.getColors().getOrDefault(nextColor, nextColor);
            String colorCode = brushManager.getColorCode(nextColor);
            msg.send(player, "color-changed", "%color%", colorCode + colorName);
            return;
        }

        if (!canUse(player, ink, msg)) return;

        if (action == Action.RIGHT_CLICK_AIR) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getType() == Material.AIR) return;

            Material offMat = offhand.getType();
            if (brushManager.isPaintable(offMat)) {
                msg.send(player, "block-not-paintable");
                return;
            }

            Material newMat = brushManager.getColoredBlock(offMat, currentColor);
            if (newMat == offMat) {
                msg.send(player, "block-color-not-found");
                return;
            }

            int stackSize = offhand.getAmount();
            ItemStack painted = new ItemStack(newMat, 1);
            if (offhand.hasItemMeta()) {
                painted.setItemMeta(offhand.getItemMeta());
            }

            if (stackSize == 1) {
                player.getInventory().setItemInOffHand(painted);
            } else {
                offhand.setAmount(stackSize - 1);
                player.getInventory().setItemInOffHand(offhand);
                player.getInventory().addItem(painted).values()
                        .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }

            consumeUse(pdc, brushManager, ink);
            meta.setLore(brushManager.buildLore(currentColor,
                    pdc.getOrDefault(brushManager.INK_KEY, PersistentDataType.INTEGER, ink - 1)));
            item.setItemMeta(meta);

            spawnPaintParticlePlayer(player);
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material blockMaterial = block.getType();
        if (brushManager.isPaintable(blockMaterial)) {
            msg.send(player, "block-not-paintable");
            return;
        }

        Material newMaterial = brushManager.getColoredBlock(blockMaterial, currentColor);
        if (newMaterial == blockMaterial) {
            msg.send(player, "block-color-not-found");
            return;
        }

        paintBlock(block, newMaterial, brushManager);
        spawnPaintParticle(block);

        consumeUse(pdc, brushManager, ink);
        meta.setLore(brushManager.buildLore(currentColor,
                pdc.getOrDefault(brushManager.INK_KEY, PersistentDataType.INTEGER, ink - 1)));
        item.setItemMeta(meta);
    }

    private boolean canUse(Player player, int ink, MessageManager msg) {
        if (ink <= 0) {
            msg.send(player, "brush-worn-out");
            return false;
        }
        return true;
    }

    private void consumeUse(PersistentDataContainer pdc, BrushManager brushManager, int ink) {
        if (ink > 0) {
            pdc.set(brushManager.INK_KEY, PersistentDataType.INTEGER, ink - 1);
        }
    }

    private void handleInkRefill(PlayerInteractEvent event, Player player, ItemStack item,
                                 BrushManager brushManager, MessageManager msg) {

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.AIR) return;

        event.setCancelled(true);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int ink    = pdc.getOrDefault(brushManager.INK_KEY, PersistentDataType.INTEGER, brushManager.getMaxInk());
        int maxInk = brushManager.getMaxInk();

        if (ink >= maxInk) {
            msg.send(player, "ink-full", "%max_ink%", String.valueOf(maxInk));
            return;
        }

        String dyeColor = brushManager.getColorForDye(offhand.getType());
        if (dyeColor == null) {
            msg.send(player, "dye-wrong-color");
            return;
        }

        int inkPerDye  = brushManager.getInkPerDye();
        int canAdd     = maxInk - ink;
        int dyesNeeded = (int) Math.ceil((double) canAdd / inkPerDye);
        int dyesUsed   = Math.min(dyesNeeded, offhand.getAmount());
        int added      = Math.min(dyesUsed * inkPerDye, canAdd);

        int newInk = ink + added;
        pdc.set(brushManager.INK_KEY, PersistentDataType.INTEGER, newInk);

        if (dyesUsed >= offhand.getAmount()) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            offhand.setAmount(offhand.getAmount() - dyesUsed);
            player.getInventory().setItemInOffHand(offhand);
        }

        String currentColor = pdc.getOrDefault(brushManager.COLOR_KEY, PersistentDataType.STRING, "WHITE");
        meta.setLore(brushManager.buildLore(currentColor, newInk));
        item.setItemMeta(meta);

        msg.send(player, "ink-refilled",
                "%amount%",   String.valueOf(added),
                "%ink%",      String.valueOf(newInk),
                "%max_ink%",  String.valueOf(maxInk));

        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
    }

    private void paintBlock(Block block, Material newMaterial, BrushManager brushManager) {
        BlockData oldData  = block.getBlockData().clone();
        BlockState oldState = block.getState();
        String suffix = brushManager.getSuffix(block.getType());

        if (suffix == null) {
            block.setType(newMaterial, false);
            restoreBasicData(block, oldData);
            refreshConnections(block);
        } else {
            switch (suffix) {
                case "_BED"                    -> { paintBed(block, newMaterial, oldData); refreshConnections(block); }
                case "_CANDLE"                 -> { paintCandle(block, newMaterial, oldData); refreshConnections(block); }
                case "_BANNER", "_WALL_BANNER" -> { paintBanner(block, newMaterial, oldData, oldState); refreshConnections(block); }
                case "_STAINED_GLASS_PANE"     -> { paintPane(block, newMaterial, oldData); refreshConnections(block); }
                default                        -> { paintDirectional(block, newMaterial, oldData); refreshConnections(block); }
            }
        }
    }

    private void restoreBasicData(Block block, BlockData oldData) {
        BlockData newData = block.getBlockData();
        if (oldData instanceof Directional oldDir && newData instanceof Directional newDir)
            newDir.setFacing(oldDir.getFacing());
        if (oldData instanceof Waterlogged oldW && newData instanceof Waterlogged newW)
            newW.setWaterlogged(oldW.isWaterlogged());
        block.setBlockData(newData, false);
    }

    private void paintBed(Block block, Material newMaterial, BlockData oldData) {
        if (!(oldData instanceof Bed oldBed)) {
            block.setType(newMaterial);
            return;
        }
        Bed.Part part    = oldBed.getPart();
        BlockFace facing = oldBed.getFacing();
        Bed.Part otherPart = part == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD;
        Block otherBlock   = block.getRelative(part == Bed.Part.HEAD ? facing.getOppositeFace() : facing);
        BlockData otherOld = otherBlock.getBlockData();
        boolean otherIsBed = otherOld instanceof Bed;

        block.setType(newMaterial, false);
        BlockData newData = block.getBlockData();
        if (newData instanceof Bed newBed) {
            newBed.setPart(part);
            newBed.setFacing(facing);
            block.setBlockData(newBed, false);
        }

        if (otherIsBed) {
            Bed otherOldBed = (Bed) otherOld;
            otherBlock.setType(newMaterial, false);
            BlockData otherNew = otherBlock.getBlockData();
            if (otherNew instanceof Bed otherNewBed) {
                otherNewBed.setPart(otherPart);
                otherNewBed.setFacing(otherOldBed.getFacing());
                otherBlock.setBlockData(otherNewBed, false);
            }
            refreshConnections(otherBlock);
        }
    }

    private void paintCandle(Block block, Material newMaterial, BlockData oldData) {
        block.setType(newMaterial, false);
        BlockData newData = block.getBlockData();
        if (oldData instanceof Candle oldCandle && newData instanceof Candle newCandle) {
            newCandle.setCandles(oldCandle.getCandles());
            newCandle.setLit(oldCandle.isLit());
            block.setBlockData(newCandle, false);
        }
    }

    private void paintBanner(Block block, Material newMaterial, BlockData oldData, BlockState oldState) {
        Rotatable oldRotatable = null;
        BlockFace oldFacing = null;

        if (oldData instanceof Rotatable rotatable) {
            oldRotatable = rotatable;
        } else if (oldData instanceof Directional dir) {
            oldFacing = dir.getFacing();
        }

        block.setType(newMaterial, false);

        BlockData newData = block.getBlockData();

        if (oldRotatable != null && newData instanceof Rotatable newRotatable) {
            newRotatable.setRotation(oldRotatable.getRotation());
            block.setBlockData(newRotatable, false);
        } else if (oldFacing != null && newData instanceof Directional newDir) {
            newDir.setFacing(oldFacing);
            block.setBlockData(newDir, false);
        }

        if (oldState instanceof Banner oldBanner) {
            BlockState newState = block.getState();
            if (newState instanceof Banner newBanner) {
                newBanner.setPatterns(oldBanner.getPatterns());
                newBanner.update(true, false);
            }
        }
    }


    private void paintPane(Block block, Material newMaterial, BlockData oldData) {
        boolean wasWaterlogged = oldData instanceof Waterlogged w && w.isWaterlogged();
        block.setType(newMaterial, false);
        BlockData newData = block.getBlockData();

        if (wasWaterlogged && newData instanceof Waterlogged newW)
            newW.setWaterlogged(true);

        if (newData instanceof MultipleFacing mf) {
            for (BlockFace face : mf.getAllowedFaces()) {
                Block neighbor   = block.getRelative(face);
                BlockData nd     = neighbor.getBlockData();
                Material nt      = neighbor.getType();
                boolean connects = nt.isSolid()
                        || nd instanceof MultipleFacing
                        || nt == Material.GLASS
                        || nt == newMaterial;
                mf.setFace(face, connects);
            }
        }
        block.setBlockData(newData, false);
    }

    private void paintDirectional(Block block, Material newMaterial, BlockData oldData) {
        block.setType(newMaterial, false);
        if (oldData instanceof Directional oldDir) {
            BlockData newData = block.getBlockData();
            if (newData instanceof Directional newDir) {
                newDir.setFacing(oldDir.getFacing());
                block.setBlockData(newData, false);
            }
        }
    }

    private void refreshConnections(Block block) {
        block.getState().update(true, true);
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.AIR)
                neighbor.getState().update(true, true);
        }
        block.getState().update(true, true);
    }

    private void spawnPaintParticle(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.4, 0.4, 0.4, 0);
    }

    private void spawnPaintParticlePlayer(Player player) {
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);
    }
}