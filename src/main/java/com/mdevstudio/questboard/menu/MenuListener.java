package com.mdevstudio.questboard.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof QuestMenu menu)) {
            return;
        }
        // Every click is cancelled, including shift, number keys and the player's own inventory.
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player
                && event.getClickedInventory() == event.getView().getTopInventory()
                && (event.getClick().isLeftClick() || event.getClick().isRightClick())) {
            menu.click(player, event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof QuestMenu) {
            event.setCancelled(true);
        }
    }
}
