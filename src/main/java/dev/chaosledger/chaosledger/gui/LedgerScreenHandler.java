package dev.chaosledger.chaosledger.gui;

import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.EconomyException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Vanilla chest-style AbstractContainerMenu showing the player their ChaosLedger balances.
 *
 * <p>This is the in-game "ledger menu": each row is one currency the player owns,
 * rendered as a labelled gold ingot stack with the balance in the item's name.</p>
 *
 * <p>NOTE: This is a first-version, read-only menu. Click interactions for
 * paying / claiming are intentionally deferred.</p>
 */
public class LedgerScreenHandler extends AbstractContainerMenu {

    private final SimpleContainer display = new SimpleContainer(27);

    public LedgerScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, playerInventory.player);
    }

    public LedgerScreenHandler(int syncId, Inventory playerInventory, Player player) {
        super(MenuType.GENERIC_9x3, syncId);
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        for (Account a : ChaosLedger.services().economy().accountsOf(uuid)) {
            displayStack(a);
        }
        // Fill the chest with the balance stacks (lazy population handles empty rows).
        for (int i = 0; i < 27; i++) {
            this.addSlot(new Slot(display, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }
        // Player's inventory below — 3 rows of 9 starting at row 4, plus hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 144));
        }
    }

    private void displayStack(Account a) {
        ItemStack stack = new ItemStack(Items.GOLD_INGOT);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(a.balance().amount() + " " + a.balance().currency())
                        .withStyle(ChatFormatting.GOLD));
        display.addItem(stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int fromIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public static void open(ServerPlayer player) {
        try {
            player.openMenu(new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("§6ChaosLedger§r — Balances");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                    return new LedgerScreenHandler(syncId, inv, p);
                }
            });
        } catch (EconomyException e) {
            player.sendSystemMessage(Component.literal("§c[ChaosLedger] " + e.getMessage()));
        }
    }
}
