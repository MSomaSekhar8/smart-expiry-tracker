package com.pantrytracker.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pantrytracker.category.Category;
import com.pantrytracker.common.BadRequestException;
import com.pantrytracker.common.NotFoundException;
import com.pantrytracker.user.User;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-DB verification of the markWasted data-integrity fix against H2
 * (pessimistic row lock, atomic transaction, snapshot behavior).
 *
 * Users/categories are seeded via JDBC because the User.role column maps to a
 * PostgreSQL-specific native enum (NAMED_ENUM) that Hibernate 6.6 cannot bind
 * on H2; that mapping is unrelated to the code under test. The markWasted
 * path itself (locked item lookup, WasteLog insert, item delete) runs through
 * the real Hibernate repositories.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "spring.datasource.url=jdbc:h2:mem:wastelock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemService.class)
class MarkWastedConcurrencyTest {

    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private WasteLogRepository wasteLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ItemService itemService;

    private UUID ownerId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        // The concurrency test commits outside the test transaction, so any
        // rows it leaves behind would pollute later tests. Always start clean.
        jdbcTemplate.update("delete from waste_log");
        jdbcTemplate.update("delete from items");
        jdbcTemplate.update("delete from categories");
        jdbcTemplate.update("delete from users");

        ownerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, email, password_hash, display_name, role, created_at, refresh_generation)
                values (?, ?, ?, ?, ?, current_timestamp, 0)""",
                ownerId, "owner-" + ownerId + "@example.com", "hash", "Owner", "USER");
        jdbcTemplate.update("""
                insert into categories (id, name, default_shelf_life_days, warning_threshold_days)
                values (?, ?, ?, ?)""",
                categoryId, "grocery-" + categoryId, 30, 3);
    }

    private Category categoryEntity() {
        Category category = new Category("grocery-" + categoryId, 30, 3);
        ReflectionTestUtils.setField(category, "id", categoryId);
        return category;
    }

    private UUID createItem(BigDecimal quantity) {
        User owner = new User("owner-" + ownerId + "@example.com", "hash", "Owner");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Item item = new Item(owner, categoryEntity(), "Milk");
        item.setQuantity(quantity);
        return itemRepository.save(item).getId();
    }

    @Test
    void markWastedSavesSnapshotAndDeletesItem() {
        UUID itemId = createItem(new BigDecimal("5"));

        itemService.markWasted(ownerId, itemId, new BigDecimal("3"), new BigDecimal("2.5"));

        assertThat(itemRepository.findById(itemId)).isEmpty();
        List<WasteLog> logs = wasteLogRepository.findAll();
        assertThat(logs).hasSize(1);
        WasteLog log = logs.get(0);
        assertThat(log.getItemName()).isEqualTo("Milk");
        assertThat(log.getQuantityWasted()).isEqualByComparingTo("3");
        assertThat(log.getEstimatedCostLost()).isEqualByComparingTo("2.5");
        assertThat(log.getUser().getId()).isEqualTo(ownerId);
        assertThat(log.getItem()).isNull();
    }

    @Test
    void markWastedOverQuantityThrowsBadRequestAndChangesNothing() {
        UUID itemId = createItem(new BigDecimal("5"));

        assertThatThrownBy(() -> itemService.markWasted(ownerId, itemId,
                new BigDecimal("6"), null))
                .isInstanceOf(BadRequestException.class);

        assertThat(wasteLogRepository.count()).isZero();
        assertThat(itemRepository.findById(itemId)).isPresent();
    }

    @Test
    void cannotWasteAnotherUsersItem() {
        UUID itemId = createItem(new BigDecimal("5"));
        UUID otherId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, email, password_hash, display_name, role, created_at, refresh_generation)
                values (?, ?, ?, ?, ?, current_timestamp, 0)""",
                otherId, "other-" + otherId + "@example.com", "hash", "Other", "USER");

        assertThatThrownBy(() -> itemService.markWasted(otherId, itemId,
                BigDecimal.ONE, null))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(wasteLogRepository.count()).isZero();
        assertThat(itemRepository.findById(itemId)).isPresent();
    }

    @Test
    void repeatedMarkWastedCreatesSingleWasteLog() {
        UUID itemId = createItem(new BigDecimal("2"));

        itemService.markWasted(ownerId, itemId, new BigDecimal("2"), null);

        assertThatThrownBy(() -> itemService.markWasted(ownerId, itemId,
                new BigDecimal("2"), null))
                .isInstanceOf(NotFoundException.class);

        assertThat(wasteLogRepository.count()).isEqualTo(1);
        assertThat(itemRepository.findById(itemId)).isEmpty();
    }

    /**
     * Two threads race to waste the same item. The pessimistic row lock
     * (SELECT ... FOR UPDATE) must serialize the transactions: the first
     * commits the WasteLog + item deletion, the second blocks on the lock,
     * then sees the item is gone and fails with NotFound.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMarkWastedCreatesExactlyOneWasteLog() throws Exception {
        UUID itemId = createItem(new BigDecimal("5"));
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<Throwable>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    itemService.markWasted(ownerId, itemId, new BigDecimal("5"), null);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int successes = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> result : results) {
            Throwable t = result.get();
            if (t == null) {
                successes++;
            } else {
                failures.add(t);
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(NotFoundException.class);

        assertThat(wasteLogRepository.count()).isEqualTo(1);
        assertThat(itemRepository.findById(itemId)).isEmpty();
    }
}