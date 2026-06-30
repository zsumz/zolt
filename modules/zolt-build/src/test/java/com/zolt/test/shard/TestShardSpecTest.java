package com.zolt.test.shard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TestShardSpecTest {
    @Test
    void parsesShardIndexAndTotal() {
        TestShardSpec shard = TestShardSpec.parse("2/4");

        assertEquals(2, shard.index());
        assertEquals(4, shard.total());
        assertEquals("2/4", shard.label());
    }

    @Test
    void blankShardMeansNoShardSelection() {
        assertNull(TestShardSpec.parse(null));
        assertNull(TestShardSpec.parse("  "));
    }

    @Test
    void rejectsMalformedShardValues() {
        TestShardException exception = assertThrows(TestShardException.class, () -> TestShardSpec.parse("first/4"));

        assertTrue(exception.getMessage().contains("Invalid --shard `first/4`"));
        assertTrue(exception.getMessage().contains("positive integer"));
    }

    @Test
    void rejectsShardIndexGreaterThanTotal() {
        TestShardException exception = assertThrows(TestShardException.class, () -> TestShardSpec.parse("5/4"));

        assertTrue(exception.getMessage().contains("Invalid --shard `5/4`"));
        assertTrue(exception.getMessage().contains("index must be less than or equal to the total"));
    }

    @Test
    void parsesShardCount() {
        assertEquals(4, TestShardSpec.parseShardCount("4"));
        assertEquals(0, TestShardSpec.parseShardCount(null));
    }
}
