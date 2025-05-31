package fr.xyness.SCS.Guis;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import fr.xyness.SCS.*;
import fr.xyness.SCS.Config.ClaimGuis;
import fr.xyness.SCS.Types.CPlayer;
import fr.xyness.SCS.Types.GuiSettings;
import fr.xyness.SCS.Types.GuiSlot;

/**
 * Class representing the Claims GUI.
 */
public class ClaimsGui implements InventoryHolder {
	
	
    // ***************
    // *  Variables  *
    // ***************

	
    /** The inventory for this GUI. */
    private final Inventory inv;
    
    /** Player */
    private final Player player;
    
    /** Instance of SimpleClaimSystem */
    private final SimpleClaimSystem instance;
    
    
    // ******************
    // *  Constructors  *
    // ******************

    
    /**
     * Main constructor for ClaimsGui.
     * 
     * @param player The player who opened the GUI.
     * @param page   The current page of the GUI.
     * @param filter The filter applied to the claims.
     * @param instance The instance of the SimpleClaimSystem plugin.
     */
    public ClaimsGui(Player player, int page, String filter, SimpleClaimSystem instance) {
    	this.instance = instance;
    	this.player = player;
    	
    	// Get title
    	GuiSettings guiSettings = ClaimGuis.gui_settings.get("claims");
    	String title = guiSettings.getTitle()
    			.replace("%page%", String.valueOf(page));
    	
    	// Create the inventory
    	inv = Bukkit.createInventory(this, guiSettings.getRows()*9, title);
        
        // Load the items asynchronously
        loadItems(page, filter).thenAccept(success -> {
        	if (success) {
        		instance.executeEntitySync(player, () -> player.openInventory(inv));
        	} else {
        		instance.executeEntitySync(player, () -> player.sendMessage(instance.getLanguage().getMessage("error")));
        	}
        })
        .exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }
    
    
    // ********************
    // *  Others Methods  *
    // ********************

    
    /**
     * Load items into the inventory.
     * 
     * @param page   The current page of the GUI.
     * @param filter The filter applied to the list.
     * @return A CompletableFuture with a boolean to check if the gui is correctly initialized.
     */
    private CompletableFuture<Boolean> loadItems(int page, String filter) {
    	
    	return CompletableFuture.supplyAsync(() -> {
    	
	    	// Get player data
	        CPlayer cPlayer = instance.getPlayerMain().getCPlayer(player.getUniqueId());
	        
	        // Get claims data
	        Map<String, Integer> owners = getOwnersByFilter(filter);
	        LinkedHashMap<String, Integer> sortedOwners = owners.entrySet()
	                .stream()
	                .sorted(Map.Entry.comparingByKey())
	                .collect(Collectors.toMap(
	                        Map.Entry::getKey,
	                        Map.Entry::getValue,
	                        (e1, e2) -> e1,
	                        LinkedHashMap::new
	                ));
	        int ownersCount = owners.size();
	        
	        // Update player data (gui)
	        cPlayer.setFilter(filter);
	        cPlayer.clearMapString();
	        cPlayer.setGuiPage(page);
	
	        // Set bottom items
    		GuiSettings guiSettings = ClaimGuis.gui_settings.get("claims");
    		int max = guiSettings.getSlots().size();
	        
	        // Items
    		List<GuiSlot> slots = new ArrayList<>(ClaimGuis.gui_slots.get("claims"));
    		for(GuiSlot slot : slots) {
    			int slot_int = slot.getSlot();
    			String key = slot.getKey();
    			String title = slot.getTitle();
    			String lore_string = slot.getLore();
    			if(key.equals("BackPage")) {
    				if(page == 1) continue;
    				title = title.replace("%page%", String.valueOf(page-1));
    				lore_string = lore_string.replace("%page%", String.valueOf(page-1));
    			}
    			if(key.equals("NextPage")) {
    				if(ownersCount <= (page*max)) continue;
    				title = title.replace("%page%", String.valueOf(page+1));
    				lore_string = lore_string.replace("%page%", String.valueOf(page+1));
    			}
    			if (key.equals("Filter")) {
    	            lore_string = lore_string
    	                    .replaceAll("%status_color_" + getStatusIndex(filter) + "%", instance.getLanguage().getMessage("status_color_active_filter"))
    	                    .replaceAll("%status_color_[^" + getStatusIndex(filter) + "]%", instance.getLanguage().getMessage("status_color_inactive_filter"));
    			}
    			List<String> lore = instance.getGuis().getLore(lore_string);
    			if(title.isBlank()) title = null;
    			if(lore.isEmpty()) lore = null;
    			if(slot.isCustomModel()) {
    				Material mat = slot.getMaterial();
    				inv.setItem(slot_int, instance.getGuis().createCustomItem(mat, title, lore, slot.getCustomModelData()));
    			} else if (slot.isCustomHead()) {
    				if(slot.getCustomTextures().equalsIgnoreCase("%player%")) {
    					ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    					SkullMeta meta = (SkullMeta) head.getItemMeta();
    					meta.setOwningPlayer(player);
    					meta.setDisplayName(title);
    					meta.setLore(lore);
    					head.setItemMeta(meta);
    					inv.setItem(slot_int, head);
    				} else if (slot.getCustomTextures().length() < 17) {
    					ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    					SkullMeta meta = (SkullMeta) head.getItemMeta();
    					OfflinePlayer targetP = Bukkit.getOfflinePlayer(slot.getCustomTextures());
    					if(targetP != null) {
    						meta.setOwningPlayer(targetP);
    					}
    					meta.setDisplayName(title);
    					meta.setLore(lore);
    					head.setItemMeta(meta);
    					inv.setItem(slot_int, head);
    				} else {
    					inv.setItem(slot_int, instance.getPlayerMain().createPlayerHeadWithTexture(slot.getCustomTextures(), title, lore));
    				}
    			} else {
					Material mat = slot.getMaterial();
					inv.setItem(slot_int, instance.getGuis().createItem(mat, title, lore));
    			}
    		}
	
	        // Prepare lore
	        List<String> loreTemplate = instance.getGuis().getLore(instance.getLanguage().getMessage("owner-claim-lore"));
	        
	        // Prepare count
	        int startItem = (page - 1) * max;
	        List<Integer> slots_i = guiSettings.getSlots();
	        int i = slots_i.get(0);
	        int count = 0;
	        int count2 = 0;
	
	        // Start loop
	        for (Map.Entry<String, Integer> entry : sortedOwners.entrySet()) {
	        	
	        	// Continue if not in the page
	            if (count++ < startItem) continue;
	            
	            // Break if bigger than end slot to not exceed
	            if (count2 > max-1) break;
	            
	            // Set new i
	            i = slots_i.get(count2);
	            count2++;
	
	            // Get owner data
	            String owner = entry.getKey();
	            int claimAmount = entry.getValue();
	            
	            // Set lore for owner
	            List<String> lore = new ArrayList<>();
	            loreTemplate.forEach(s -> {
	            	String l = s.replace("%claim-amount%", instance.getMain().getNumberSeparate(String.valueOf(claimAmount)));
	            	lore.add(l);
	            });
	            lore.add(instance.getLanguage().getMessage("owner-claim-access"));
	            
	            // Set owner head item
	        	ItemStack item = instance.getPlayerMain().getPlayerHead(owner);
	        	if(item == null) {
	        		item = new ItemStack(Material.PLAYER_HEAD);
	        	}
	        	if(item.hasItemMeta() && item.getItemMeta() != null) {
	        		if(item.getItemMeta() instanceof SkullMeta meta) {
	    	            meta.setDisplayName(instance.getLanguage().getMessage("owner-claim-title").replace("%owner%", owner));
	    	            meta.setLore(lore);
	    	            item.setItemMeta(meta);
	        		} else {
	        			ItemMeta meta = item.getItemMeta();
	    	            meta.setDisplayName(instance.getLanguage().getMessage("owner-claim-title").replace("%owner%", owner));
	    	            meta.setLore(lore);
	    	            item.setItemMeta(meta);
	        		}
	        	}
	            inv.setItem(i, item);
	            
	            // Add the owner to map string for gui clicking
	            cPlayer.addMapString(i, owner);
	            
	            i++;
	        }
	        
	        return true;

    	});

    }

    /**
     * Get the owners based on the filter.
     * 
     * @param filter The filter to apply.
     * @return A map of owners and their claim count.
     */
    private Map<String, Integer> getOwnersByFilter(String filter) {
        switch (filter) {
            case "sales":
                return instance.getMain().getClaimsOwnersWithSales();
            case "online":
                return instance.getMain().getClaimsOnlineOwners();
            case "offline":
                return instance.getMain().getClaimsOfflineOwners();
            default:
                return instance.getMain().getClaimsOwnersGui();
        }
    }

    /**
     * Get the index of the current filter.
     * 
     * @param filter The current filter.
     * @return The index of the filter.
     */
    private int getStatusIndex(String filter) {
        switch (filter) {
            case "sales":
                return 2;
            case "online":
                return 3;
            case "offline":
                return 4;
            default:
                return 1;
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
