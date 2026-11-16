package dev.chaosledger.chaosledger;

import dev.chaosledger.chaosledger.audit.AuditLogger;
import dev.chaosledger.chaosledger.economy.EconomyEngine;
import dev.chaosledger.chaosledger.economy.MarketEngine;
import dev.chaosledger.chaosledger.economy.PriceEngine;
import dev.chaosledger.chaosledger.quest.QuestEngine;
import dev.chaosledger.chaosledger.reward.ItemDelivery;
import dev.chaosledger.chaosledger.reward.RewardEngine;
import dev.chaosledger.chaosledger.storage.AccountRepository;
import dev.chaosledger.chaosledger.storage.AuditRepository;
import dev.chaosledger.chaosledger.storage.CurrencyRepository;
import dev.chaosledger.chaosledger.storage.Database;
import dev.chaosledger.chaosledger.storage.MarketRepository;
import dev.chaosledger.chaosledger.storage.QuestProgressRepository;
import dev.chaosledger.chaosledger.storage.QuestRepository;
import dev.chaosledger.chaosledger.storage.RewardRepository;
import dev.chaosledger.chaosledger.storage.TransactionRepository;

import java.sql.Connection;

/**
 * Singleton container that wires the storage layer and the engines together.
 *
 * <p>Built once by the Fabric entrypoint after the database has been opened.
 * Holds references so the HTTP layer and commands can reach the engines via
 * {@link ChaosLedger#services()}.</p>
 */
public final class ChaosLedgerServices implements AutoCloseable {

    private final Database database;
    private final Connection connection;
    private final CurrencyRepository currencies;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final MarketRepository market;
    private final QuestRepository quests;
    private final QuestProgressRepository progress;
    private final RewardRepository rewards;
    private final AuditRepository auditRepo;
    private final AuditLogger audit;
    private final PriceEngine priceEngine;
    private final EconomyEngine economy;
    private final MarketEngine marketEngine;
    private final RewardEngine rewardEngine;
    private final QuestEngine questEngine;

    public ChaosLedgerServices(ChaosLedgerConfig config, ItemDelivery itemDelivery) {
        this.database = new Database(config.databaseFile());
        this.connection = database.open();
        this.currencies = new CurrencyRepository(connection);
        this.accounts = new AccountRepository(connection);
        this.transactions = new TransactionRepository(connection);
        this.market = new MarketRepository(connection);
        this.quests = new QuestRepository(connection);
        this.progress = new QuestProgressRepository(connection);
        this.rewards = new RewardRepository(connection);
        this.auditRepo = new AuditRepository(connection);
        this.audit = new AuditLogger(auditRepo);
        this.priceEngine = new PriceEngine();
        this.economy = new EconomyEngine(accounts, transactions, currencies, audit, config);
        this.marketEngine = new MarketEngine(market, economy, priceEngine, audit, config);
        this.rewardEngine = new RewardEngine(economy, rewards, audit, itemDelivery);
        this.questEngine = new QuestEngine(quests, progress, rewardEngine, audit);

        // Seed the default currency if missing.
        if (currencies.findByCode(config.defaultCurrency()).isEmpty()) {
            currencies.save(new dev.chaosledger.chaosledger.economy.Currency(
                    config.defaultCurrency(),
                    "Coins",
                    config.defaultCurrency(),
                    config.startingBalance()));
        }
    }

    public Database database() { return database; }
    public CurrencyRepository currencies() { return currencies; }
    public AccountRepository accounts() { return accounts; }
    public TransactionRepository transactions() { return transactions; }
    public MarketRepository market() { return market; }
    public QuestRepository quests() { return quests; }
    public QuestProgressRepository progress() { return progress; }
    public RewardRepository rewards() { return rewards; }
    public AuditRepository auditRepo() { return auditRepo; }
    public AuditLogger audit() { return audit; }
    public PriceEngine priceEngine() { return priceEngine; }
    public EconomyEngine economy() { return economy; }
    public MarketEngine marketEngine() { return marketEngine; }
    public RewardEngine rewardEngine() { return rewardEngine; }
    public QuestEngine questEngine() { return questEngine; }

    @Override
    public void close() {
        database.close();
    }
}
