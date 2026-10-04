package dev.heap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HexIdsTest {

    @Test
    void roundTripsLosslessly() {
        long[] ids = {0L, 1L, 0x7fff_ffff_ffff_ffffL, 0xffff_ffff_ffff_ffffL, 0x1234_5678_9abc_def0L};
        for (long id : ids) {
            assertEquals(id, HexIds.parse(HexIds.toHex(id)));
        }
    }

    @Test
    void acceptsPlainHexAndRejectsGarbage() {
        assertEquals(255L, HexIds.parse("ff"));
        assertNull(HexIds.parse("0xzz"));
        assertNull(HexIds.parse(""));
        assertNull(HexIds.parse("12345678901234567"));
    }
}
