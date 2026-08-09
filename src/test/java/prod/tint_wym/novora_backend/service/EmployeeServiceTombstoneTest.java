package prod.tint_wym.novora_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Soft-delete tombstones must fit the column widths the JPA entities declare:
 *   <ul>
 *     <li>{@code employees.email}    — {@code VARCHAR(255)}</li>
 *     <li>{@code users.email}        — {@code VARCHAR(255)}</li>
 *     <li>{@code employees.employee_code} — {@code VARCHAR(20)}</li>
 *   </ul>
 *
 * The previous tombstone of {@code "DEL-<8 hex>-<originalCode>"} silently overflowed VARCHAR(20)
 * for every auto-generated 10-char employee code (final length 23) — every delete returned 500.
 * These tests pin the size + shape so the regression cannot reappear.
 */
class EmployeeServiceTombstoneTest {

    @Test
    void emailTombstoneFitsVarchar255() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String tombstone = EmployeeService.buildEmailTombstone(id);
        assertEquals("deleted-aaaaaaaabbbbccccddddeeeeeeeeeeee@tombstone.local", tombstone);
        assertEquals(56, tombstone.length());
        assertTrue(tombstone.length() < 255, "must fit users.email/employees.email VARCHAR(255)");
        assertTrue(tombstone.startsWith("deleted-"), "prefix guards idempotency");
    }

    @Test
    void codeTombstoneFitsVarchar20() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String tombstone = EmployeeService.buildCodeTombstone(id);
        assertEquals("DEL-aaaaaaaabbbb", tombstone);
        assertEquals(16, tombstone.length());
        assertTrue(tombstone.length() <= 20,
                "must fit employees.employee_code VARCHAR(20); regression: this was 23+ chars");
        assertTrue(tombstone.startsWith("DEL-"), "prefix guards idempotency");
    }

    @Test
    void tombstonesAreDeterministic() {
        UUID id = UUID.randomUUID();
        assertEquals(EmployeeService.buildEmailTombstone(id), EmployeeService.buildEmailTombstone(id));
        assertEquals(EmployeeService.buildCodeTombstone(id), EmployeeService.buildCodeTombstone(id));
    }

    @Test
    void tombstonesDifferAcrossEmployees() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertNotEquals(EmployeeService.buildEmailTombstone(a), EmployeeService.buildEmailTombstone(b));
        assertNotEquals(EmployeeService.buildCodeTombstone(a), EmployeeService.buildCodeTombstone(b));
    }

    @Test
    void everyRandomUuidProducesAFittingCode() {
        // Defensive: brute-force a few thousand UUIDs to confirm the 12-char prefix never blows out.
        for (int i = 0; i < 5_000; i++) {
            String t = EmployeeService.buildCodeTombstone(UUID.randomUUID());
            assertTrue(t.length() <= 20, "tombstone overflowed: " + t);
        }
    }
}
