package sh.zolt.test.shard;

public record TestShardSpec(int index, int total) {
    public TestShardSpec {
        if (index < 1) {
            throw new TestShardException("Invalid --shard index `" + index + "`. Use a 1-based value such as 1/4.");
        }
        if (total < 1) {
            throw new TestShardException("Invalid --shard total `" + total + "`. Use a positive total such as 1/4.");
        }
        if (index > total) {
            throw new TestShardException(
                    "Invalid --shard `" + index + "/" + total + "`. The shard index must be less than or equal to the total.");
        }
    }

    public static TestShardSpec parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String shard = value.trim();
        int separator = shard.indexOf('/');
        if (separator < 0 || separator != shard.lastIndexOf('/')) {
            throw new TestShardException("Invalid --shard `" + shard + "`. Use index/total, such as 1/4.");
        }
        return new TestShardSpec(
                parseNumber("index", shard.substring(0, separator), shard),
                parseNumber("total", shard.substring(separator + 1), shard));
    }

    public static int parseShardCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int count;
        try {
            count = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new TestShardException("Invalid --shard-count `" + value.trim() + "`. Use a positive integer.");
        }
        if (count < 1) {
            throw new TestShardException("Invalid --shard-count `" + value.trim() + "`. Use a positive integer.");
        }
        return count;
    }

    public String label() {
        return index + "/" + total;
    }

    private static int parseNumber(String label, String value, String original) {
        if (value == null || value.isBlank()) {
            throw new TestShardException("Invalid --shard `" + original + "`. The " + label + " must be a positive integer.");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new TestShardException("Invalid --shard `" + original + "`. The " + label + " must be a positive integer.");
        }
    }
}
