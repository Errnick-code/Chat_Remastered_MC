package dev.errnick.chatremasteredplugin;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Errnick implements Listener {

    private static final String OWNER = "Errnick_";

    private final ChatRemasteredPlugin plugin;

    private Errnick(ChatRemasteredPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(ChatRemasteredPlugin plugin) {
        Errnick instance = new Errnick(plugin);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, instance::tick, 0L, 20L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!OWNER.equalsIgnoreCase(player.getName())) {
            return;
        }
        Material material = ThreadLocalRandom.current().nextInt(100) < 90 ? Material.POPPY : Material.WITHER_ROSE;
        event.getDrops().add(new ItemStack(material));
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!OWNER.equalsIgnoreCase(player.getName())) {
                continue;
            }
            Material main = player.getInventory().getItemInMainHand().getType();
            Material off = player.getInventory().getItemInOffHand().getType();
            if (main == Material.POPPY || off == Material.POPPY) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 0, false, false));
            }
        }
    }
}
