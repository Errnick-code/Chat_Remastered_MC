package dev.errnick.chatremastered;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Errnick implements ModInitializer {

    private static final String OWNER = "Errnick_";

    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }
            if (!OWNER.equalsIgnoreCase(player.getName().getString())) {
                return;
            }
            ItemStack stack = new ItemStack(player.getRandom().nextInt(100) < 90 ? Items.POPPY : Items.WITHER_ROSE);
            player.drop(stack, false, false);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!OWNER.equalsIgnoreCase(player.getName().getString())) {
                    continue;
                }
                if (player.getMainHandItem().is(Items.POPPY) || player.getOffhandItem().is(Items.POPPY)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 60, 0, false, false));
                }
            }
        });
    }
}
