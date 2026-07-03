package sh.zolt.test.shard;

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
    void rejectsMissingShardPartsAndExtraSeparators() {
        TestShardException missingIndex = assertThrows(TestShardException.class, () -> TestShardSpec.parse("/4"));
        TestShardException missingTotal = assertThrows(TestShardException.class, () -> TestShardSpec.parse("1/ "));
        TestShardException extraSeparator = assertThrows(TestShardException.class, () -> TestShardSpec.parse("1/2/3"));

        assertTrue(missingIndex.getMessage().contains("index must be a positive integer"));
        assertTrue(missingTotal.getMessage().contains("total must be a positive integer"));
        assertTrue(extraSeparator.getMessage().contains("Use index/total"));
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

    @Test
    void rejectsInvalidShardCounts() {
        TestShardException nonNumeric = assertThrows(TestShardException.class, () -> TestShardSpec.parseShardCount("many"));
        TestShardException zero = assertThrows(TestShardException.class, () -> TestShardSpec.parseShardCount("0"));

        assertEquals("Invalid --shard-count `many`. Use a positive integer.", nonNumeric.getMessage());
        assertEquals("Invalid --shard-count `0`. Use a positive integer.", zero.getMessage());
    }
}
