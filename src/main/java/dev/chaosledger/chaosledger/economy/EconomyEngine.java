package dev.chaosledger.chaosledger.economy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.chaosledger.chaosledger.ChaosLedgerConfig;
import dev.chaosledger.chaosledger.audit.AuditEntry;
import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.storage.AccountRepository;
import dev.chaosledger.chaosledger.storage.CurrencyRepository;
import dev.chaosledger.chaosledger.storage.TransactionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative economy service.
 *
 * <p>Every money movement goes through this class. It performs the transactional
 * dance of:</p>
 * <ol>
 *     <li>load both accounts (creating them lazily),</li>
 *     <li>mutate balances in memory,</li>
 *     <li>{@link AccountRepository#updateBalance(Account)} for each,</li>
 *     <li>{@link TransactionRepository#insert} so the ledger row commits in the
 *         same transaction as the balance change,</li>
 *     <li>emit an {@link AuditEntry}.</li>
 * </ol>
 *
 * <p>Operations are <b>not</b> thread-safe on their own. Callers must run them on
 * the Minecraft server thread or hold the appropriate lock.</p>
 */
public final class EconomyEngine {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CurrencyRepository currencies;
    private final AuditLogger audit;
    private final ChaosLedgerConfig config;
    private final Gson gson = new Gson();

    public EconomyEngine(AccountRepository accounts,
                        TransactionRepository transactions,
                        CurrencyRepository currencies,
                        AuditLogger audit,
                        ChaosLedgerConfig config) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.currencies = currencies;
        this.audit = audit;
        this.config = config;
    }

    /** Lookup the player's account in the configured currency, creating it on first touch. */
    public Account accountOf(UUID ownerUuid, String ownerName, String currency) {
        Currency meta = currencies.findByCode(currency)
                .orElseThrow(() -> new EconomyException("Unknown currency: " + currency));
        Account existing = accounts.findByOwner(ownerUuid, currency).orElse(null);
        if (existing != null) {
            return existing;
        }
        Money opening = new Money(meta.defaultStarting(), currency);
        return accounts.getOrCreate(ownerUuid, ownerName, currency, opening);
    }

    public List<Account> accountsOf(UUID ownerUuid) {
        return accounts.findByOwner(ownerUuid);
    }

    public List<Account> top(String currency, int limit) {
        return accounts.top(currency, limit);
    }

    /** Mint money into a player's account (admin grant, quest reward, server event). */
    public Transaction grant(UUID ownerUuid, String ownerName, String currency, Money amount, String reason) {
        requireCurrency(amount, currency);
        Account account = accountOf(ownerUuid, ownerName, currency);
        Account updated = account.withBalance(account.balance().plus(amount));
        accounts.updateBalance(updated);
        Transaction tx = new Transaction(
                0, null, ownerUuid, amount, reason, "GRANT", null, System.currentTimeMillis());
        long id = transactions.insert(tx);
        Transaction stored = new Transaction(id, null, ownerUuid, amount, reason, "GRANT", null, tx.createdAt());

        JsonObject payload = new JsonObject();
        payload.addProperty("currency", currency);
        payload.addProperty("amount", amount.amount());
        payload.addProperty("reason", reason);
        audit.log(new AuditEntry(0, ownerUuid, ownerName, "ECONOMY_GRANT",
                ownerUuid.toString(), gson.toJson(payload), System.currentTimeMillis()));
        return stored;
    }

    /** Burn money from a player's account (admin revoke, fee, quest cost). */
    public Transaction charge(UUID ownerUuid, String ownerName, String currency, Money amount, String reason, String category) {
        requireCurrency(amount, currency);
        Account account = accountOf(ownerUuid, ownerName, currency);
        Money newBalance = account.balance().minus(amount);
        if (newBalance.isNegative() && !config.allowNegativeBalance()) {
            throw new EconomyException("Insufficient funds: " + account.balance() + " < " + amount);
        }
        Account updated = account.withBalance(newBalance);
        accounts.updateBalance(updated);
        Transaction tx = new Transaction(
                0, ownerUuid, null, amount, reason, category, null, System.currentTimeMillis());
        long id = transactions.insert(tx);
        Transaction stored = new Transaction(id, ownerUuid, null, amount, reason, category, null, tx.createdAt());

        JsonObject payload = new JsonObject();
        payload.addProperty("currency", currency);
        payload.addProperty("amount", amount.amount());
        payload.addProperty("reason", reason);
        audit.log(new AuditEntry(0, ownerUuid, ownerName, "ECONOMY_CHARGE",
                ownerUuid.toString(), gson.toJson(payload), System.currentTimeMillis()));
        return stored;
    }

    /** Transfer money between two players. */
    public Transaction pay(UUID fromUuid, String fromName, UUID toUuid, String toName,
                           String currency, Money amount, String reason) {
        requireCurrency(amount, currency);
        if (amount.isNegative()) {
            throw new EconomyException("Cannot pay a negative amount");
        }
        if (fromUuid.equals(toUuid)) {
            throw new EconomyException("Cannot pay yourself");
        }
        Account from = accountOf(fromUuid, fromName, currency);
        Account to   = accountOf(toUuid,   toName,   currency);

        Money newFrom = from.balance().minus(amount);
        if (newFrom.isNegative() && !config.allowNegativeBalance()) {
            throw new EconomyException("Insufficient funds: " + from.balance() + " < " + amount);
        }
        Money newTo = to.balance().plus(amount);

        // Order matters: debit before credit so an exception leaves no phantom money.
        accounts.updateBalance(from.withBalance(newFrom));
        accounts.updateBalance(to.withBalance(newTo));

        Transaction tx = new Transaction(
                0, fromUuid, toUuid, amount, reason, "PAY", null, System.currentTimeMillis());
        long id = transactions.insert(tx);
        Transaction stored = new Transaction(id, fromUuid, toUuid, amount, reason, "PAY", null, tx.createdAt());

        JsonObject payload = new JsonObject();
        payload.addProperty("currency", currency);
        payload.addProperty("amount", amount.amount());
        payload.addProperty("from", fromUuid.toString());
        payload.addProperty("to", toUuid.toString());
        payload.addProperty("reason", reason);
        audit.log(new AuditEntry(0, fromUuid, fromName, "ECONOMY_PAY",
                toUuid.toString(), gson.toJson(payload), System.currentTimeMillis()));
        return stored;
    }

    public List<Transaction> history(UUID playerUuid, int limit) {
        return transactions.forPlayer(playerUuid, limit);
    }

    public List<Transaction> recent(String currency, int limit) {
        return transactions.recent(currency, limit);
    }

    public Optional<Currency> currency(String code) {
        return currencies.findByCode(code);
    }

    public List<Currency> currencies() {
        return currencies.all();
    }

    public void defineCurrency(Currency currency) {
        currencies.save(currency);
    }

    private void requireCurrency(Money amount, String currency) {
        if (!amount.currency().equals(currency)) {
            throw new EconomyException("Money currency " + amount.currency() + " does not match " + currency);
        }
        if (amount.isNegative()) {
            throw new EconomyException("Money amount must not be negative");
        }
    }
}
