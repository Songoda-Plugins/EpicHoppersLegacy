package com.songoda.epichoppers.hopper.levels.modules;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.songoda.core.SongodaPlugin;
import com.songoda.core.gui.GuiManager;
import com.songoda.core.locale.Locale;
import com.songoda.third_party.com.cryptomorin.xseries.XMaterial;
import com.songoda.core.utils.TextUtils;
import com.songoda.epichoppers.hopper.Hopper;
import com.songoda.epichoppers.settings.Settings;
import com.songoda.epichoppers.utils.Methods;
import com.songoda.epichoppers.utils.StorageContainerCache;
import com.songoda.third_party.com.cryptomorin.xseries.particles.XParticle;
import com.songoda.ultimatestacker.api.UltimateStackerApi;
import dev.rosewood.rosestacker.api.RoseStackerAPI;
import dev.rosewood.rosestacker.stack.StackedItem;
import net.vortexdevelopment.vortexstacker.api.VortexStackerApi;
import net.vortexdevelopment.vortexstacker.api.modules.stack.item.StackedItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModuleSuction extends Module {
    private static final List<UUID> BLACKLIST = new ArrayList<>();

    private static final boolean WILD_STACKER = Bukkit.getPluginManager().isPluginEnabled("WildStacker");
    private static final boolean ULTIMATE_STACKER = Bukkit.getPluginManager().isPluginEnabled("UltimateStacker");
    private static final boolean ROSE_STACKER = Bukkit.getPluginManager().isPluginEnabled("RoseStacker");
    private static final boolean VORTEX_STACKER = Bukkit.getPluginManager().isPluginEnabled("VortexStacker");

    private final int maxSearchRadius;

    public ModuleSuction(SongodaPlugin plugin, GuiManager guiManager, int amount) {
        super(plugin, guiManager);
        this.maxSearchRadius = amount;
    }

    @Override
    public String getName() {
        return "Suction";
    }

    @Override
    public void run(Hopper hopper, StorageContainerCache.Cache hopperCache) {
        double radius = getRadius(hopper) + .5;

        if (!isEnabled(hopper)) {
            return;
        }

        Set<Item> itemsToSuck = hopper.getLocation().getWorld().getNearbyEntities(hopper.getLocation().add(0.5, 0.5, 0.5), radius, radius, radius)
                .stream()
                .filter(entity -> entity.getType() == EntityType.DROPPED_ITEM
                        && !entity.isDead()
                        && entity.getTicksLived() >= ((Item) entity).getPickupDelay()
                        && entity.getLocation().getBlock().getType() != Material.HOPPER)
                .map(Item.class::cast)
                .collect(Collectors.toSet());

        if (itemsToSuck.isEmpty()) {
            return;
        }

        boolean filterEndpoint = hopper.getFilter().getEndPoint() != null;

        Inventory hopperInventory = null;
        if (Settings.EMIT_INVENTORYPICKUPITEMEVENT.getBoolean()) {
            InventoryHolder inventoryHolder = (InventoryHolder) hopper.getBlock().getState();
            hopperInventory = Bukkit.createInventory(inventoryHolder, InventoryType.HOPPER);
        }

        for (Item item : itemsToSuck) {
            ItemStack itemStack = item.getItemStack();

            if (item.getPickupDelay() == 0) {
                item.setPickupDelay(25);
                continue;
            }

            if (itemStack.getType().name().contains("SHULKER_BOX")) {
                return;
            }

            // CRITICAL FIX: Prevent hoppers from being sucked by other hoppers
            // This prevents the item loss bug when breaking multiple hoppers quickly
            if (itemStack.getType() == Material.HOPPER) {
                continue;
            }

            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() &&
                    itemStack.getItemMeta().getDisplayName().startsWith("***")) {
                return; //Compatibility with Shop instance: https://www.spigotmc.org/resources/shop-a-simple-intuitive-shop-instance.9628/
            }

            if (BLACKLIST.contains(item.getUniqueId())) {
                return;
            }

            // respect filter if no endpoint
            if (!filterEndpoint
                    && !(hopper.getFilter().getWhiteList().isEmpty() && hopper.getFilter().getBlackList().isEmpty())) {
                // this hopper has a filter with no rejection endpoint, so don't absorb disallowed items
                // whitelist has priority
                if (!hopper.getFilter().getWhiteList().isEmpty()) {
                    // is this item on the whitelist?
                    if (hopper.getFilter().getWhiteList().stream().noneMatch(filterItem -> Methods.isSimilarMaterial(itemStack, filterItem))) {
                        // nope!
                        continue;
                    }
                } else {
                    // check the blacklist
                    if (hopper.getFilter().getBlackList().stream().anyMatch(filterItem -> Methods.isSimilarMaterial(itemStack, filterItem))) {
                        // don't grab this, then
                        continue;
                    }
                }
            }

            if (Settings.EMIT_INVENTORYPICKUPITEMEVENT.getBoolean()) {
                hopperInventory.setContents(hopperCache.cachedInventory);
                InventoryPickupItemEvent pickupEvent = new InventoryPickupItemEvent(hopperInventory, item);
                Bukkit.getPluginManager().callEvent(pickupEvent);
                if (pickupEvent.isCancelled()) {
                    continue;
                }
            }

            // try to add the items to the hopper
            int toAdd, added = hopperCache.addAny(itemStack, toAdd = getActualItemAmount(item));
            if (added == 0) {
                return;
            }

            // items added ok!
            if (added == toAdd) {
                item.remove();
            } else {
                // update the item's total
                updateAmount(item, toAdd - added);

                // wait before trying to add again
                BLACKLIST.add(item.getUniqueId());
                Bukkit.getScheduler().runTaskLater(this.plugin,
                        () -> BLACKLIST.remove(item.getUniqueId()), 10L);
            }

            float xx = (float) (0 + (Math.random() * .1));
            float yy = (float) (0 + (Math.random() * .1));
            float zz = (float) (0 + (Math.random() * .1));
            item.getWorld().spawnParticle(XParticle.FLAME.get(), item.getLocation(), 5, xx, yy, zz, 0);
        }
    }

    private int getActualItemAmount(Item item) {
        if (ULTIMATE_STACKER) {
            return UltimateStackerApi.getStackedItemManager().getActualItemAmount(item);
        } else if (WILD_STACKER) {
            return WildStackerAPI.getItemAmount(item);
        } else if(ROSE_STACKER) {
            StackedItem stackedItem = RoseStackerAPI.getInstance().getStackedItem(item);
            if (stackedItem != null) {
                return stackedItem.getStackSize();
            }
        } else if (VORTEX_STACKER) {
            // Placeholder for VortexStacker logic if available
            StackedItemManager stackedItemManager = VortexStackerApi.getItemManager();
            if (stackedItemManager != null) {
                net.vortexdevelopment.vortexstacker.api.modules.stack.item.StackedItem stackedItem = stackedItemManager.getStackedItem(item);
                if (stackedItem != null) {
                    BigInteger amount = stackedItem.getAmount();
                    // Safely convert BigInteger to int, capping at Integer.MAX_VALUE
                    return amount.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                            ? Integer.MAX_VALUE
                            : amount.intValue();
                }
            }
        }
        return item.getItemStack().getAmount();
    }

    private void updateAmount(Item item, int amount) {
        if (ULTIMATE_STACKER) {
            UltimateStackerApi.getStackedItemManager().updateStack(item, amount);
        } else if (WILD_STACKER) {
            WildStackerAPI.getStackedItem(item).setStackAmount(amount, true);
        } else if (VORTEX_STACKER) {
            StackedItemManager stackedItemManager = VortexStackerApi.getItemManager();
            if (stackedItemManager != null) {
                net.vortexdevelopment.vortexstacker.api.modules.stack.item.StackedItem stackedItem =stackedItemManager.getStackedItem(item);
                if (stackedItem != null) {
                    stackedItem.setAmount(BigInteger.valueOf(amount));
                }
            }
        } else {
            item.getItemStack().setAmount(Math.min(amount, item.getItemStack().getMaxStackSize()));
        }
    }

    public static boolean isBlacklisted(UUID uuid) {
        return BLACKLIST.contains(uuid);
    }

    @Override
    public ItemStack getGUIButton(Hopper hopper) {
        Locale locale = this.plugin.getLocale();
        ItemStack item = XMaterial.CAULDRON.parseItem();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(locale.getMessage("interface.hopper.suctiontitle").toText());
        List<String> loreSuction = TextUtils.formatLore(
                locale.getMessage("interface.hopper.suctionlore")
                        .processPlaceholder("status", isEnabled(hopper) ? locale.getMessage("general.word.enabled").toText() : locale.getMessage("general.word.disabled").toText())
                        .processPlaceholder("radius", getRadius(hopper))
                        .toText()
        );
        meta.setLore(loreSuction);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void runButtonPress(Player player, Hopper hopper, ClickType type) {
        if (type == ClickType.LEFT) {
            toggleEnabled(hopper);
        } else if (type == ClickType.RIGHT) {
            int setRadius = getRadius(hopper);
            if (setRadius >= this.maxSearchRadius) {
                setRadius(hopper, 1);
            } else {
                setRadius(hopper, ++setRadius);
            }
        }
    }


    private boolean isEnabled(Hopper hopper) {
        Object obj = getData(hopper, "enabled");
        return obj == null || (boolean) obj;
    }

    private void toggleEnabled(Hopper hopper) {
        saveData(hopper, "enabled", !isEnabled(hopper));
    }

    private int getRadius(Hopper hopper) {
        Object foundRadius = getData(hopper, "radius");
        return foundRadius == null ? this.maxSearchRadius : (int) foundRadius;
    }

    private void setRadius(Hopper hopper, int radius) {
        saveData(hopper, "radius", radius);
    }

    @Override
    public List<Material> getBlockedItems(Hopper hopper) {
        return null;
    }

    @Override
    public String getDescription() {
        return this.plugin.getLocale()
                .getMessage("interface.hopper.suction")
                .processPlaceholder("suction", this.maxSearchRadius)
                .toText();
    }
}
