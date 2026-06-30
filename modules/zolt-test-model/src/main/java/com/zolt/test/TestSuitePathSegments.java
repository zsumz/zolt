package com.zolt.test;

import com.zolt.test.shard.TestShardSpec;

public final class TestSuitePathSegments {
    private TestSuitePathSegments() {
    }

    public static String suiteSegment(String suiteName) {
        String text = suiteName == null || suiteName.isBlank() ? "all" : suiteName;
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.'
                    || character == '_'
                    || character == '-') {
                safe.append(character);
            } else {
                safe.append('_');
            }
        }
        return safe.toString();
    }

    public static String shardSegment(TestShardSpec shard) {
        return "shard-" + shard.index() + "-of-" + shard.total();
    }
}
