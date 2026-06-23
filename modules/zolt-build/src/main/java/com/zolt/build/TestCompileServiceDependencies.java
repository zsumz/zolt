package com.zolt.build;

import com.zolt.doctor.JdkChecker;
import com.zolt.generated.ProtobufGeneratedSourceService;
import com.zolt.resolve.ResolveService;

final class TestCompileServiceDependencies {
    private final BuildService buildService;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkChecker jdkDetector;
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final ProtobufGeneratedSourceService protobufGeneratedSourceService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final TestCompileSourceExecutor sourceExecutor;

    private TestCompileServiceDependencies(
            TestInputDependencies testInputDependencies,
            GeneratedSourceDependencies generatedSourceDependencies,
            TestSourceExecutorDependencies executorDependencies) {
        this.buildService = testInputDependencies.buildService();
        this.sourceDiscoverer = testInputDependencies.sourceDiscoverer();
        this.resourceCopier = testInputDependencies.resourceCopier();
        this.buildFingerprintService = testInputDependencies.buildFingerprintService();
        this.jdkDetector = generatedSourceDependencies.jdkDetector();
        this.openApiGeneratedSourceService = generatedSourceDependencies.openApiGeneratedSourceService();
        this.protobufGeneratedSourceService = generatedSourceDependencies.protobufGeneratedSourceService();
        this.incrementalCompileStateRecorder = executorDependencies.incrementalCompileStateRecorder();
        this.sourceExecutor = executorDependencies.sourceExecutor();
    }

    static TestCompileServiceDependencies create(JdkChecker jdkDetector, ResolveService resolveService) {
        return create(
                new BuildService(jdkDetector, resolveService),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new BuildFingerprintService(),
                jdkDetector,
                new JavacRunner(),
                new GroovyCompilerRunner(),
                new OpenApiGeneratedSourceService(jdkDetector));
    }

    static TestCompileServiceDependencies create(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner,
            OpenApiGeneratedSourceService openApiGeneratedSourceService) {
        IncrementalCompileStateRecorder incrementalCompileStateRecorder = new IncrementalCompileStateRecorder();
        return new TestCompileServiceDependencies(
                new TestInputDependencies(
                        buildService,
                        sourceDiscoverer,
                        resourceCopier,
                        buildFingerprintService),
                new GeneratedSourceDependencies(
                        jdkDetector,
                        openApiGeneratedSourceService,
                        new ProtobufGeneratedSourceService()),
                new TestSourceExecutorDependencies(
                        incrementalCompileStateRecorder,
                        new TestCompileSourceExecutor(
                                javacRunner,
                                groovyCompilerRunner,
                                incrementalCompileStateRecorder,
                                new IncrementalCompilePlanner())));
    }

    BuildService buildService() {
        return buildService;
    }

    SourceDiscoverer sourceDiscoverer() {
        return sourceDiscoverer;
    }

    ResourceCopier resourceCopier() {
        return resourceCopier;
    }

    BuildFingerprintService buildFingerprintService() {
        return buildFingerprintService;
    }

    JdkChecker jdkDetector() {
        return jdkDetector;
    }

    OpenApiGeneratedSourceService openApiGeneratedSourceService() {
        return openApiGeneratedSourceService;
    }

    ProtobufGeneratedSourceService protobufGeneratedSourceService() {
        return protobufGeneratedSourceService;
    }

    IncrementalCompileStateRecorder incrementalCompileStateRecorder() {
        return incrementalCompileStateRecorder;
    }

    TestCompileSourceExecutor sourceExecutor() {
        return sourceExecutor;
    }

    private record TestInputDependencies(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService) {
    }

    private record GeneratedSourceDependencies(
            JdkChecker jdkDetector,
            OpenApiGeneratedSourceService openApiGeneratedSourceService,
            ProtobufGeneratedSourceService protobufGeneratedSourceService) {
    }

    private record TestSourceExecutorDependencies(
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            TestCompileSourceExecutor sourceExecutor) {
    }
}
