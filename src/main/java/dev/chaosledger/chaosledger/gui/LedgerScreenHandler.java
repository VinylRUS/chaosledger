package dev.chaosledger.chaosledger.gui;

import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.EconomyException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Vanilla chest-style ScreenHandler showing the player their ChaosLedger balances.
 *
 * <p>This is the in-game "ledger menu": each row is one currency the player owns,
 * rendered as a labelled gold ingot stack with the balance in the item's name.</p>
 *
 * <p>NOTE: This is a first-version, read-only menu. Click interactions for
 * paying / claiming are intentionally deferred.</p>
 */
public class LedgerScreenHandler extends ScreenHandler {

    private final SimpleInventory display = new SimpleInventory(27);

    public LedgerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, playerInventory.player);
    }

    public LedgerScreenHandler(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);
        UUID uuid = player.getUuid();
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
        stack.setCustomName(Text.literal(a.balance().amount() + " " + a.balance().currency())
                .formatted(Formatting.GOLD));
        display.addStack(stack);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int fromIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public static void open(ServerPlayerEntity player) {
        try {
            player.openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("§6ChaosLedger§r — Balances");
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                    return new LedgerScreenHandler(syncId, inv, p);
                }
            });
        } catch (EconomyException e) {
            player.sendMessage(Text.literal("§c[ChaosLedger] " + e.getMessage()));
        }
    }
}
