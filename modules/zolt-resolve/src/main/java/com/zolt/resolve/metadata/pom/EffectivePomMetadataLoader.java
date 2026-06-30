package com.zolt.resolve.metadata.pom;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.RawPom;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.metrics.EffectivePomLoadMetricsSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public final class EffectivePomMetadataLoader {
    private final ParentPomChainLoader parentPomChainLoader;
    private final EffectivePomInheritanceBuilder effectivePomInheritanceBuilder;
    private final ImportedBomDependencyManagementExpander importedBomDependencyManagementExpander;
    private final Map<String, EffectiveRawPom> metadata = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<EffectiveRawPom>> metadataLoads = new ConcurrentHashMap<>();

    public EffectivePomMetadataLoader(
            ParentPomChainLoader parentPomChainLoader,
            EffectivePomInheritanceBuilder effectivePomInheritanceBuilder,
            ImportedBomDependencyManagementExpander importedBomDependencyManagementExpander) {
        this.parentPomChainLoader = parentPomChainLoader;
        this.effectivePomInheritanceBuilder = effectivePomInheritanceBuilder;
        this.importedBomDependencyManagementExpander = importedBomDependencyManagementExpander;
    }

    public EffectiveRawPom load(
            Coordinate coordinate,
            List<String> importStack,
            Function<Coordinate, RawPom> rawPomLoader,
            EffectivePomLoadMetricsSink metrics) {
        String key = coordinate.toString();
        EffectiveRawPom cached = metadata.get(key);
        if (cached != null) {
            metrics.recordEffectivePomCacheHit();
            return cached;
        }
        if (importStack.contains(key)) {
            throw new ResolveException(
                    "Imported BOM cycle detected: "
                            + String.join(" -> ", importStack)
                            + " -> "
                            + key
                            + ". Remove one of the import-scoped dependencyManagement entries.");
        }

        CompletableFuture<EffectiveRawPom> pending = new CompletableFuture<>();
        CompletableFuture<EffectiveRawPom> existing = metadataLoads.putIfAbsent(key, pending);
        if (existing != null) {
            metrics.recordEffectivePomCacheHit();
            return awaitEffectivePom(key, existing);
        }
        metrics.recordEffectivePomCacheMiss();
        long started = System.nanoTime();
        try {
            RawPom rawPom = rawPomLoader.apply(coordinate);
            List<RawPom> parents = parentPomChainLoader.load(rawPom, rawPomLoader);
            EffectiveRawPom base = effectivePomInheritanceBuilder.build(coordinate, rawPom, parents);
            List<String> nextStack = new ArrayList<>(importStack);
            nextStack.add(key);
            EffectiveRawPom effective = new EffectiveRawPom(
                    rawPom,
                    parents,
                    base.groupId(),
                    base.version(),
                    base.properties(),
                    importedBomDependencyManagementExpander.expand(
                            base,
                            nextStack,
                            (importedCoordinate, importedStack) ->
                                    load(importedCoordinate, importedStack, rawPomLoader, metrics)));
            metadata.put(key, effective);
            pending.complete(effective);
            return effective;
        } catch (RuntimeException exception) {
            pending.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            pending.completeExceptionally(error);
            throw error;
        } finally {
            metadataLoads.remove(key, pending);
            metrics.recordEffectivePomBuild(elapsedSince(started));
        }
    }

    private static EffectiveRawPom awaitEffectivePom(String key, CompletableFuture<EffectiveRawPom> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolveException(
                    "Interrupted while waiting for in-flight effective POM metadata "
                            + key
                            + ". Try again.",
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ResolveException(
                    "Could not load in-flight effective POM metadata "
                            + key
                            + ". Try again.",
                    cause);
        }
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }
}
