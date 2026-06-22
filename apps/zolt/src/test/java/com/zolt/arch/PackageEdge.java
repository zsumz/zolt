package com.zolt.arch;

record PackageEdge(String source, String target) implements Comparable<PackageEdge> {
    String describe() {
        return source + " -> " + target;
    }

    @Override
    public int compareTo(PackageEdge other) {
        int sourceComparison = source.compareTo(other.source);
        if (sourceComparison != 0) {
            return sourceComparison;
        }
        return target.compareTo(other.target);
    }
}
