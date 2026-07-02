package com.zolt.explain.gradle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class GradleScriptBlocks {
    private GradleScriptBlocks() {
    }

    static Optional<String> topLevelBlock(String content, String name) {
        return topLevelBlocks(content, name).stream().findFirst();
    }

    static List<String> topLevelBlocks(String content, String name) {
        return blocks(content).stream()
                .filter(block -> block.path().equals(List.of(name)))
                .map(GradleBlock::content)
                .toList();
    }

    static List<String> blocksAtPath(String content, List<String> path) {
        return blocks(content).stream()
                .filter(block -> block.path().equals(path))
                .map(GradleBlock::content)
                .toList();
    }

    static String withoutNestedBlocks(String content) {
        StringBuilder output = new StringBuilder(content.length());
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (quote != 0) {
                output.append(depth == 0 ? character : replacement(character));
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                output.append(depth == 0 ? character : replacement(character));
                quote = character;
                continue;
            }
            if (character == '{') {
                output.append(replacement(character));
                depth++;
                continue;
            }
            if (character == '}') {
                depth = Math.max(0, depth - 1);
                output.append(replacement(character));
                continue;
            }
            output.append(depth == 0 ? character : replacement(character));
        }
        return output.toString();
    }

    private static List<GradleBlock> blocks(String content) {
        List<GradleBlock> blocks = new ArrayList<>();
        List<BlockFrame> stack = new ArrayList<>();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == '{') {
                String name = blockNameBefore(content, index);
                List<String> path = new ArrayList<>();
                if (!stack.isEmpty()) {
                    path.addAll(stack.getLast().path());
                }
                path.add(name);
                stack.add(new BlockFrame(name, path, index + 1));
                continue;
            }
            if (character == '}' && !stack.isEmpty()) {
                BlockFrame frame = stack.removeLast();
                blocks.add(new GradleBlock(
                        frame.name(),
                        frame.path(),
                        frame.contentStart(),
                        content.substring(frame.contentStart(), index)));
            }
        }
        for (BlockFrame frame : stack) {
            blocks.add(new GradleBlock(
                    frame.name(),
                    frame.path(),
                    frame.contentStart(),
                    content.substring(frame.contentStart())));
        }
        blocks.sort(Comparator.comparingInt(GradleBlock::contentStart));
        return blocks;
    }

    private static String blockNameBefore(String content, int braceIndex) {
        int end = previousNonWhitespace(content, braceIndex - 1);
        if (end < 0) {
            return "";
        }
        if (content.charAt(end) == ')') {
            int openParen = matchingOpenParen(content, end);
            if (openParen >= 0) {
                return selectorBefore(content, openParen - 1);
            }
        }
        return selectorBefore(content, end);
    }

    private static int previousNonWhitespace(String content, int index) {
        int current = index;
        while (current >= 0 && Character.isWhitespace(content.charAt(current))) {
            current--;
        }
        return current;
    }

    private static int matchingOpenParen(String content, int closeParen) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = closeParen; index >= 0; index--) {
            char character = content.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == ')') {
                depth++;
            } else if (character == '(') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String selectorBefore(String content, int index) {
        int end = previousNonWhitespace(content, index);
        if (end < 0) {
            return "";
        }
        if (content.charAt(end) == '>') {
            end = genericStart(content, end) - 1;
            end = previousNonWhitespace(content, end);
        }
        int start = end;
        while (start >= 0) {
            char character = content.charAt(start);
            if (!Character.isLetterOrDigit(character) && character != '_' && character != '-' && character != '.') {
                break;
            }
            start--;
        }
        return content.substring(start + 1, end + 1);
    }

    private static int genericStart(String content, int genericEnd) {
        int depth = 0;
        for (int index = genericEnd; index >= 0; index--) {
            char character = content.charAt(index);
            if (character == '>') {
                depth++;
            } else if (character == '<') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return genericEnd;
    }

    private static char replacement(char character) {
        return character == '\n' || character == '\r' ? character : ' ';
    }

    private record GradleBlock(String name, List<String> path, int contentStart, String content) {}

    private record BlockFrame(String name, List<String> path, int contentStart) {}
}
