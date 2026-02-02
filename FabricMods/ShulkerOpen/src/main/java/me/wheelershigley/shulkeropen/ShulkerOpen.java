package me.wheelershigley.shulkeropen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShulkerOpen implements ModInitializer {
    public static final String MOD_ID = "shulkeropen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            // Check if it's a shulker box
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return ActionResult.PASS;
            }
            if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
                return ActionResult.PASS;
            }

            // Shift+right-click = place normally (let vanilla handle)
            if (player.isSneaking()) {
                return ActionResult.PASS;
            }

            // Non-sneaking right-click = open inventory
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                openShulkerInventory(serverPlayer, stack);
            }

            return ActionResult.SUCCESS;
        });

        LOGGER.info("Shulker Open loaded - shift+right-click to place, right-click to open!");
    }

    private void openShulkerInventory(ServerPlayerEntity player, ItemStack shulkerStack) {
        // Get the container contents from the shulker box
        ContainerComponent container = shulkerStack.getOrDefault(
            DataComponentTypes.CONTAINER,
            ContainerComponent.DEFAULT
        );

        // Create inventory and populate with shulker contents
        ShulkerItemInventory inventory = new ShulkerItemInventory(shulkerStack, 27);
        container.copyTo(inventory.getHeldStacks());

        // Get custom name if present
        Text displayName = shulkerStack.contains(DataComponentTypes.CUSTOM_NAME)
            ? shulkerStack.getName()
            : Text.translatable("container.shulkerBox");

        // Open the screen
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, playerEntity) ->
                new GenericContainerScreenHandler(
                    ScreenHandlerType.GENERIC_9X3,
                    syncId,
                    playerInventory,
                    inventory,
                    3
                ),
            displayName
        ));
    }
}
