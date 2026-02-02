package me.wheelershigley.bemine;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BeMine implements ModInitializer {
    public static final String MOD_ID = "bemine";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final List<ValentineEffect> activeEffects = new ArrayList<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommand(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<ValentineEffect> iterator = activeEffects.iterator();
            while (iterator.hasNext()) {
                ValentineEffect effect = iterator.next();
                if (effect.tick()) {
                    iterator.remove();
                }
            }
        });

        LOGGER.info("BeMine loaded! Use /bemine <player> to spread the love!");
    }

    private void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("bemine")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                        ServerPlayerEntity source = context.getSource().getPlayer();

                        ValentineEffect effect = new ValentineEffect(target);
                        activeEffects.add(effect);

                        if (source != null) {
                            source.sendMessage(Text.literal("§d§l<3 §r§dSending love to " + target.getName().getString() + "!"));
                        }
                        target.sendMessage(Text.literal("§d§l<3 §r§dSomeone loves you! §l<3"));

                        return 1;
                    })
                )
        );
    }
}
