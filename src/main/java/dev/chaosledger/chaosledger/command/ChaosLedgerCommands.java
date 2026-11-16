package dev.chaosledger.chaosledger.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.chaosledger.chaosledger.ChaosLedger;
import dev.chaosledger.chaosledger.economy.Account;
import dev.chaosledger.chaosledger.economy.EconomyException;
import dev.chaosledger.chaosledger.economy.Money;
import dev.chaosledger.chaosledger.economy.Transaction;
import dev.chaosledger.chaosledger.quest.QuestException;
import dev.chaosledger.chaosledger.quest.QuestProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers all {@code /cl} commands.
 *
 * <p>Layout:</p>
 * <pre>
 *   /cl balance [currency]
 *   /cl pay   &lt;player&gt; &lt;amount&gt; [currency] [reason...]
 *   /cl transactions [limit]
 *   /cl quest list
 *   /cl quest start   &lt;questId&gt;
 *   /cl quest claim   &lt;questId&gt;
 *   /cl quest progress &lt;questId&gt;
 *   /cl admin grant   &lt;player&gt; &lt;amount&gt; [currency] [reason...]
 *   /cl admin charge  &lt;player&gt; &lt;amount&gt; [currency] [reason...]
 *   /cl admin top     [currency] [limit]
 *   /cl admin reload
 * </pre>
 */
public final class ChaosLedgerCommands {

    private ChaosLedgerCommands() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("cl")
                .then(literal("balance")
                        .executes(ctx -> balance(ctx, defaultCurrency()))
                        .then(argument("currency", StringArgumentType.word())
                                .executes(ctx -> balance(ctx, StringArgumentType.getString(ctx, "currency")))))
                .then(literal("pay")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("amount", DoubleArgumentType.doubleArg(0.01d))
                                        .executes(ctx -> pay(ctx, defaultCurrency(), "Transfer"))
                                        .then(argument("currency", StringArgumentType.word())
                                                .executes(ctx -> pay(ctx, StringArgumentType.getString(ctx, "currency"), "Transfer"))
                                                .then(argument("reason", StringArgumentType.greedyString())
                                                        .executes(ctx -> pay(ctx, StringArgumentType.getString(ctx, "currency"),
                                                                StringArgumentType.getString(ctx, "reason")))))))
                .then(literal("transactions")
                        .executes(ctx -> transactions(ctx, 5))
                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> transactions(ctx, IntegerArgumentType.getInteger(ctx, "limit")))))
                .then(literal("quest")
                        .then(literal("list").executes(ChaosLedgerCommands::questList))
                        .then(literal("start")
                                .then(argument("questId", StringArgumentType.word())
                                        .executes(ctx -> questStart(ctx, StringArgumentType.getString(ctx, "questId")))))
                        .then(literal("claim")
                                .then(argument("questId", StringArgumentType.word())
                                        .executes(ctx -> questClaim(ctx, StringArgumentType.getString(ctx, "questId")))))
                        .then(literal("progress")
                                .then(argument("questId", StringArgumentType.word())
                                        .executes(ctx -> questProgress(ctx, StringArgumentType.getString(ctx, "questId"))))))
                .then(literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("grant")
                                .then(argument("player", StringArgumentType.word())
                                        .then(argument("amount", DoubleArgumentType.doubleArg(0.01d))
                                                .executes(ctx -> adminGrant(ctx, defaultCurrency(), "Admin grant"))
                                                .then(argument("currency", StringArgumentType.word())
                                                        .executes(ctx -> adminGrant(ctx, StringArgumentType.getString(ctx, "currency"), "Admin grant"))
                                                        .then(argument("reason", StringArgumentType.greedyString())
                                                                .executes(ctx -> adminGrant(ctx, StringArgumentType.getString(ctx, "currency"),
                                                                        StringArgumentType.getString(ctx, "reason"))))))))
                        .then(literal("charge")
                                .then(argument("player", StringArgumentType.word())
                                        .then(argument("amount", DoubleArgumentType.doubleArg(0.01d))
                                                .executes(ctx -> adminCharge(ctx, defaultCurrency(), "Admin charge"))
                                                .then(argument("currency", StringArgumentType.word())
                                                        .executes(ctx -> adminCharge(ctx, StringArgumentType.getString(ctx, "currency"), "Admin charge"))
                                                        .then(argument("reason", StringArgumentType.greedyString())
                                                                .executes(ctx -> adminCharge(ctx, StringArgumentType.getString(ctx, "currency"),
                                                                        StringArgumentType.getString(ctx, "reason"))))))))
                        .then(literal("top")
                                .executes(ctx -> adminTop(ctx, defaultCurrency(), 10))
                                .then(argument("currency", StringArgumentType.word())
                                        .executes(ctx -> adminTop(ctx, StringArgumentType.getString(ctx, "currency"), 10))
                                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                                .executes(ctx -> adminTop(ctx, StringArgumentType.getString(ctx, "currency"),
                                                        IntegerArgumentType.getInteger(ctx, "limit"))))))
                        .then(literal("reload").executes(ChaosLedgerCommands::adminReload)))
                .then(literal("help").executes(ChaosLedgerCommands::help))));
    }

    private static String defaultCurrency() {
        return ChaosLedger.config().defaultCurrency();
    }

    // ----- Economy -----

    private static int balance(CommandContext<CommandSourceStack> ctx, String currency) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        try {
            Account a = ChaosLedger.services().economy().accountOf(uuid, name, currency);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Balance: §e" + a.balance().amount() + " " + a.balance().currency()), false);
            return 1;
        } catch (EconomyException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int pay(CommandContext<CommandSourceStack> ctx, String currency, String reason) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer sender = src.getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        var target = src.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            src.sendFailure(Component.literal("§cPlayer not found: " + targetName));
            return 0;
        }
        try {
            Money money = new Money(amount, currency);
            Transaction tx = ChaosLedger.services().economy().pay(
                    sender.getUUID(), sender.getName().getString(),
                    target.getUUID(), target.getName().getString(),
                    currency, money, reason);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Sent §e" + money.amount() + " " + money.currency()
                    + "§r to §b" + targetName + "§r (tx #" + tx.id() + ")"), false);
            target.sendSystemMessage(Component.literal("§6[ChaosLedger]§r Received §e" + money.amount() + " " + money.currency()
                    + "§r from §b" + src.getTextName() + "§r: " + reason));
            return 1;
        } catch (EconomyException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int transactions(CommandContext<CommandSourceStack> ctx, int limit) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        var txs = ChaosLedger.services().economy().history(uuid, limit);
        if (txs.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7[ChaosLedger] No transactions yet."), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Last " + txs.size() + " transactions:"), false);
        for (Transaction t : txs) {
            String dir = t.fromUuid().isPresent() && t.fromUuid().get().equals(uuid) ? "§cout §r" : "§ain §r";
            String counterparty = t.fromUuid().isPresent() && t.fromUuid().get().equals(uuid)
                    ? (t.toUuid().isPresent() ? shortUuid(t.toUuid().get()) : "server")
                    : (t.fromUuid().isPresent() ? shortUuid(t.fromUuid().get()) : "server");
            src.sendSuccess(() -> Component.literal(" §7#" + t.id() + " " + dir
                    + t.amount().amount() + " " + t.amount().currency()
                    + " §7" + t.category() + " §8" + counterparty + " — " + t.reason()), false);
        }
        return 1;
    }

    // ----- Quests -----

    private static int questList(CommandContext<CommandSourceStack> ctx) {
        var quests = ChaosLedger.services().questEngine().all();
        if (quests.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[ChaosLedger] No quests defined."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Quests (" + quests.size() + "):"), false);
        for (var q : quests) {
            ctx.getSource().sendSuccess(() -> Component.literal(" §b" + q.id() + "§r §7(" + q.objectives().size() + " objectives)§r — " + q.title()), false);
        }
        return 1;
    }

    private static int questStart(CommandContext<CommandSourceStack> ctx, String questId) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        try {
            QuestProgress p = ChaosLedger.services().questEngine().start(uuid, questId);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Quest §b" + questId + "§r is now §e" + p.state() + "§r."), false);
            return 1;
        } catch (QuestException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int questClaim(CommandContext<CommandSourceStack> ctx, String questId) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        try {
            ChaosLedger.services().questEngine().claim(uuid, name, questId);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Reward claimed for §b" + questId + "§r."), false);
            return 1;
        } catch (QuestException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int questProgress(CommandContext<CommandSourceStack> ctx, String questId) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        try {
            QuestProgress p = ChaosLedger.services().questEngine().progressOf(uuid, questId);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Quest §b" + questId + "§r state: §e" + p.state() + "§r"), false);
            var objectives = ChaosLedger.services().questEngine().quest(questId).orElseThrow().objectives();
            for (int i = 0; i < objectives.size(); i++) {
                double done = i < p.progress().size() ? p.progress().get(i) : 0d;
                var o = objectives.get(i);
                src.sendSuccess(() -> Component.literal(" §7- " + o.description()
                        + " [" + (int) done + "/" + (int) o.required() + "]"), false);
            }
            return 1;
        } catch (QuestException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    // ----- Admin -----

    private static int adminGrant(CommandContext<CommandSourceStack> ctx, String currency, String reason) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        var target = src.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            src.sendFailure(Component.literal("§cPlayer not found: " + targetName));
            return 0;
        }
        try {
            Money money = new Money(amount, currency);
            Transaction tx = ChaosLedger.services().economy().grant(
                    target.getUUID(), target.getName().getString(), currency, money, reason);
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Granted §e" + money.amount() + " " + money.currency()
                    + "§r to §b" + targetName + "§r (tx #" + tx.id() + ")"), true);
            return 1;
        } catch (EconomyException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int adminCharge(CommandContext<CommandSourceStack> ctx, String currency, String reason) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        var target = src.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            src.sendFailure(Component.literal("§cPlayer not found: " + targetName));
            return 0;
        }
        try {
            Money money = new Money(amount, currency);
            Transaction tx = ChaosLedger.services().economy().charge(
                    target.getUUID(), target.getName().getString(), currency, money, reason, "ADMIN");
            src.sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Charged §e" + money.amount() + " " + money.currency()
                    + "§r from §b" + targetName + "§r (tx #" + tx.id() + ")"), true);
            return 1;
        } catch (EconomyException e) {
            src.sendFailure(Component.literal("§c[ChaosLedger] " + e.getMessage()));
            return 0;
        }
    }

    private static int adminTop(CommandContext<CommandSourceStack> ctx, String currency, int limit) {
        var top = ChaosLedger.services().economy().top(currency, limit);
        if (top.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[ChaosLedger] No accounts in " + currency + " yet."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Top balances in §e" + currency + "§r:"), false);
        int rank = 1;
        for (Account a : top) {
            final int r = rank++;
            ctx.getSource().sendSuccess(() -> Component.literal(" §e#" + r + "§r §b" + a.ownerName()
                    + "§r — " + a.balance().amount() + " " + a.balance().currency()), false);
        }
        return 1;
    }

    private static int adminReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6[ChaosLedger]§r Config is loaded at startup; restart the server to apply changes."), false);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6ChaosLedger commands:§r"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl balance [currency]§r — your balance"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl pay <player> <amount> [currency] [reason]§r — transfer money"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl transactions [limit]§r — your transaction history"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl quest list§r — available quests"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl quest start <id>§r — start a quest"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl quest claim <id>§r — claim the reward"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl quest progress <id>§r — see your progress"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/cl admin grant|charge|top|reload§r — admin tools"), false);
        return 1;
    }

    private static String shortUuid(UUID uuid) {
        String s = uuid.toString();
        return s.substring(0, Math.min(8, s.length()));
    }
}
