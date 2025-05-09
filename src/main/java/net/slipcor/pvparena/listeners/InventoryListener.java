package net.slipcor.pvparena.listeners;

import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.regions.RegionProtection;
import net.slipcor.pvparena.managers.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>Inventory Listener class</pre>
 *
 * @author slipcor
 * @version v0.10.2
 */

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public static void onInventoryClick(final InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();

        final Arena arena = ArenaPlayer.fromPlayer(player).getArena();

        if (arena == null) {
            return;
        }

        debug(arena, player, "InventoryClick: arena player");

        if (!arena.getConfig().getBoolean(CFG.PLAYER_MAYCHANGEARMOR)) {
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getInventory().getType() == InventoryType.CRAFTING) {
            // we are inside the standard
            if (event.getRawSlot() != 5) {
                return;
            }
            if (arena.getConfig().getBoolean(CFG.USES_WOOLHEAD)) {
                event.setCancelled(true);
                return;
            }
        }


        try {
            arena.getGoal().checkInventory(event);
        } catch (GameplayException e) {
            debug(player, "onInventoryClick cancelled by goal: " + arena.getGoal().getName());
            return;
        }

        if (!BlockListener.isProtected(arena, event.getWhoClicked().getLocation(), event, RegionProtection.INVENTORY)) {
            // we don't need no protection => out!
            return;
        }

        if (event.isShiftClick()) {
            // we never want shift clicking!
            event.setCancelled(true);
            return;
        }

        debug(arena, player, "cancelling!");
        // player is carrying a flag
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public static void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != InventoryType.CHEST) {
            return;
        }

        Arena arena;
        if(event.getSource().getLocation() != null) {
            arena = ArenaManager.getArenaByRegionLocation(new PABlockLocation(event.getSource().getLocation()));
        } else if (event.getDestination().getLocation() != null) {
            arena = ArenaManager.getArenaByRegionLocation(new PABlockLocation(event.getDestination().getLocation()));
        } else {
            return;
        }

        if (arena == null || !arena.isFightInProgress()) {
            return;
        }

        try {
            arena.getGoal().checkItemTransfer(event);
        } catch (GameplayException e) {
            debug(arena, "onInventoryMoveItem cancelled by goal: " + arena.getGoal().getName());
        }
    }
}
