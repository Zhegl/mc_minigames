package me.petr1furious.potatominigames.games.pillars;

import java.util.Arrays;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

/** Equipment is a visual snapshot; none of the owner's actual items move. */
final class PerkDecoys {
    private PerkDecoys() { }
    static void disguise(Mannequin body, Player owner) {
        body.setProfile(ResolvableProfile.resolvableProfile(owner.getPlayerProfile()));
        body.customName(owner.name()); body.setCustomNameVisible(true); body.setDescription(null);
        body.setImmovable(true); body.setGravity(false); body.setCollidable(false);
        body.setSilent(true); body.setCanPickupItems(false); body.setRemoveWhenFarAway(false);
        body.setMainHand(owner.getMainHand());
        var gear = body.getEquipment();
        gear.setArmorContents(Arrays.stream(owner.getInventory().getArmorContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new));
        gear.setItemInMainHand(owner.getInventory().getItemInMainHand().clone());
        gear.setItemInOffHand(owner.getInventory().getItemInOffHand().clone());
        gear.setHelmetDropChance(0); gear.setChestplateDropChance(0);
        gear.setLeggingsDropChance(0); gear.setBootsDropChance(0);
        gear.setItemInMainHandDropChance(0); gear.setItemInOffHandDropChance(0);
    }
}
