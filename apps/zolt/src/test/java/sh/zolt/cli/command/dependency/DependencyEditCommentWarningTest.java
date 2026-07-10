package sh.zolt.cli.command.dependency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DependencyEditCommentWarningTest {
    @Test
    void detectsCommentsOutsideStringsOnly() {
        assertTrue(DependencyEditCommentWarning.containsTomlComment("""
                [project]
                name = "demo" # keep the user note visible before rewrite
                """));
        assertTrue(DependencyEditCommentWarning.containsTomlComment("""
                # top-level note
                [project]
                name = "demo"
                """));
        assertFalse(DependencyEditCommentWarning.containsTomlComment("""
                [project]
                name = "demo # not a comment"
                description = '''literal # not a comment'''
                long = \"""
                still # not a comment
                \"""
                """));
    }
}
