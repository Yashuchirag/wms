package com.warehouse.wms;

import com.warehouse.wms.dto.ReconciliationMismatchDTO;
import com.warehouse.wms.exception.InsufficientStockException;
import com.warehouse.wms.service.InventoryLedgerService;
import com.warehouse.wms.service.InventoryLedgerService.LedgerRef;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("itest")
class InventoryLedgerTest {

    private static final long WAREHOUSE = 1;

    @LocalServerPort
    private int port;

    @Autowired
    private InventoryLedgerService ledger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long itemId;
    private long receiving;
    private long bin;
    private long pack;

    @BeforeEach
    void createItem() {
        itemId = jdbcTemplate.queryForObject(
                "INSERT INTO items (sku, name, priority) VALUES ('SKU-LEDGER', 'Ledger test item', 'LOW') RETURNING id",
                Long.class);
        receiving = locationId("RCV-01");
        bin = locationId("Z-A1-01");
        pack = locationId("PACK-01");
    }

    @AfterEach
    void deleteRows() {
        jdbcTemplate.update("DELETE FROM inventory_txn WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM inventory WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", itemId);
    }

    @Test
    void eachOperationChangesBalancesAndWritesExactlyOneLedgerRow() {
        long receivingRow = ledger.receive(WAREHOUSE, itemId, receiving, 20, LedgerRef.NONE).getId();
        assertLedgerRows(1);

        long binRow = ledger.move(receivingRow, bin, 5, LedgerRef.NONE).getId();
        assertLedgerRows(2);

        ledger.allocate(binRow, 3, LedgerRef.NONE);
        assertLedgerRows(3);

        long packRow = ledger.pick(binRow, pack, 2, LedgerRef.NONE).getId();
        assertLedgerRows(4);

        ledger.ship(packRow, 2, LedgerRef.NONE);
        assertLedgerRows(5);

        ledger.deallocate(binRow, 1, LedgerRef.NONE);
        assertLedgerRows(6);

        ledger.adjust(receivingRow, -4, "CYCLE_COUNT", LedgerRef.NONE);
        assertLedgerRows(7);

        assertEquals(List.of("RECEIPT", "MOVE", "ALLOCATE", "PICK", "SHIP", "DEALLOCATE", "ADJUST_OUT"),
                jdbcTemplate.queryForList("SELECT txn_type FROM inventory_txn WHERE item_id = ? ORDER BY id",
                        String.class, itemId));
        assertBalance(receivingRow, 11, 0);
        assertBalance(binRow, 3, 0);
        assertBalance(packRow, 0, 0);
        assertEquals(List.of(), mismatchesForItem());
    }

    @Test
    void rejectedOperationsWriteNothing() {
        long row = ledger.receive(WAREHOUSE, itemId, receiving, 5, LedgerRef.NONE).getId();

        assertThrows(InsufficientStockException.class, () -> ledger.allocate(row, 6, LedgerRef.NONE));
        assertThrows(InsufficientStockException.class, () -> ledger.ship(row, 1, LedgerRef.NONE));
        assertThrows(InsufficientStockException.class, () -> ledger.move(row, bin, 6, LedgerRef.NONE));
        assertThrows(IllegalArgumentException.class, () -> ledger.move(row, receiving, 1, LedgerRef.NONE));
        assertThrows(IllegalArgumentException.class, () -> ledger.allocate(row, 0, LedgerRef.NONE));

        assertLedgerRows(1);
        assertBalance(row, 5, 0);
    }

    @Test
    void ledgerReconcilesAfterARandomSequenceOfOperations() {
        // Fixed seed so a failure can be replayed
        Random random = new Random(20261107);
        List<Long> locations = List.of(receiving, bin, pack);
        ledger.receive(WAREHOUSE, itemId, receiving, 50, LedgerRef.NONE);

        int succeeded = 1;
        for (int i = 0; i < 300; i++) {
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM inventory WHERE item_id = ? ORDER BY id", Long.class, itemId);
            long row = rows.get(random.nextInt(rows.size()));
            long location = locations.get(random.nextInt(locations.size()));
            int qty = 1 + random.nextInt(10);
            try {
                switch (random.nextInt(7)) {
                    case 0 -> ledger.receive(WAREHOUSE, itemId, location, qty, LedgerRef.NONE);
                    case 1 -> ledger.move(row, location, qty, LedgerRef.NONE);
                    case 2 -> ledger.allocate(row, qty, LedgerRef.NONE);
                    case 3 -> ledger.deallocate(row, qty, LedgerRef.NONE);
                    case 4 -> ledger.pick(row, location, qty, LedgerRef.NONE);
                    case 5 -> ledger.ship(row, qty, LedgerRef.NONE);
                    default -> ledger.adjust(row, random.nextBoolean() ? qty : -qty, "CYCLE_COUNT", LedgerRef.NONE);
                }
                succeeded++;
            } catch (InsufficientStockException | IllegalArgumentException rejected) {
                // Expected for moves to the same location and for more than is available or reserved
            }
        }

        assertTrue(succeeded > 100, "too few operations succeeded to prove anything: " + succeeded);
        assertLedgerRows(succeeded);
        assertEquals(List.of(), mismatchesForItem());
    }

    @Test
    void reconciliationEndpointReportsADrift() throws Exception {
        long row = ledger.receive(WAREHOUSE, itemId, receiving, 8, LedgerRef.NONE).getId();
        jdbcTemplate.update("UPDATE inventory SET quantity = quantity + 1 WHERE id = ?", row);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/inventory/reconciliation")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JSONArray mismatches = new JSONArray(response.body());
        boolean reported = false;
        for (int i = 0; i < mismatches.length(); i++) {
            var mismatch = mismatches.getJSONObject(i);
            if (mismatch.getLong("itemId") == itemId) {
                assertEquals(receiving, mismatch.getLong("locationId"));
                assertEquals(8, mismatch.getLong("ledgerQuantity"));
                assertEquals(9, mismatch.getLong("balanceQuantity"));
                reported = true;
            }
        }
        assertTrue(reported, "drift for the test item was not reported: " + response.body());
    }

    private List<ReconciliationMismatchDTO> mismatchesForItem() {
        return ledger.findMismatches().stream().filter(mismatch -> mismatch.itemId() == itemId).toList();
    }

    private void assertLedgerRows(int expected) {
        assertEquals(expected, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_txn WHERE item_id = ?", Integer.class, itemId));
    }

    private void assertBalance(long inventoryId, int quantity, int reserved) {
        assertEquals(List.of(quantity, reserved), jdbcTemplate.queryForObject(
                "SELECT quantity, reserved_quantity FROM inventory WHERE id = ?",
                (rs, rowNum) -> List.of(rs.getInt(1), rs.getInt(2)), inventoryId));
    }

    private long locationId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE warehouse_id = ? AND code = ?", Long.class, WAREHOUSE, code);
    }
}
