package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.doctor.JdkChecker;
import com.zolt.generated.ProtobufGeneratedSourceService;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.resolve.ResolveService;

final class BuildServiceDependencies {
    private final ResolveService resolveService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildMetadataGenerator buildMetadataGenerator;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkChecker jdkDetector;
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final ProtobufGeneratedSourceService protobufGeneratedSourceService;
    private final SpringBootAotGenerationService springBootAotGenerationService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final MainCompileSourceExecutor sourceExecutor;

    private BuildServiceDependencies(
            ResolveDependencies resolveDependencies,
            OutputDependencies outputDependencies,
            GeneratedSourceDependencies generatedSourceDependencies) {
        this.resolveService = resolveDependencies.resolveService();
        this.lockfileReader = resolveDependencies.lockfileReader();
        this.classpathBuilder = resolveDependencies.classpathBuilder();
        this.sourceDiscoverer = outputDependencies.sourceDiscoverer();
        this.resourceCopier = outputDependencies.resourceCopier();
        this.buildMetadataGenerator = outputDependencies.buildMetadataGenerator();
        this.buildFingerprintService = outputDependencies.buildFingerprintService();
        this.jdkDetector = generatedSourceDependencies.jdkDetector();
        this.openApiGeneratedSourceService = generatedSourceDependencies.openApiGeneratedSourceService();
        this.protobufGeneratedSourceService = generatedSourceDependencies.protobufGeneratedSourceService();
        this.springBootAotGenerationService = generatedSourceDependencies.springBootAotGenerationService();
        this.incrementalCompileStateRecorder = generatedSourceDependencies.incrementalCompileStateRecorder();
        this.sourceExecutor = generatedSourceDependencies.sourceExecutor();
    }

    static BuildServiceDependencies create(JdkChecker jdkDetector, ResolveService resolveService) {
        JavacRunner javacRunner = new JavacRunner();
        IncrementalCompileStateRecorder incrementalCompileStateRecorder = new IncrementalCompileStateRecorder();
        return new BuildServiceDependencies(
                new ResolveDependencies(
                        resolveService,
                        new ZoltLockfileReader(),
                        new ClasspathBuilder()),
                new OutputDependencies(
                        new SourceDiscoverer(),
                        new ResourceCopier(),
                        new BuildMetadataGenerator(),
                        new BuildFingerprintService()),
                new GeneratedSourceDependencies(
                        jdkDetector,
                        new OpenApiGeneratedSourceService(jdkDetector),
                        new ProtobufGeneratedSourceService(),
                        new SpringBootAotGenerationService(javacRunner),
                        incrementalCompileStateRecorder,
                        new MainCompileSourceExecutor(
                                javacRunner,
                                incrementalCompileStateRecorder,
                                new IncrementalCompilePlanner())));
    }

    ResolveService resolveService() {
        return resolveService;
    }

    ZoltLockfileReader lockfileReader() {
        return lockfileReader;
    }

    ClasspathBuilder classpathBuilder() {
        return classpathBuilder;
    }

    SourceDiscoverer sourceDiscoverer() {
        return sourceDiscoverer;
    }

    ResourceCopier resourceCopier() {
        return resourceCopier;
    }

    BuildMetadataGenerator buildMetadataGenerator() {
        return buildMetadataGenerator;
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

    SpringBootAotGenerationService springBootAotGenerationService() {
        return springBootAotGenerationService;
    }

    IncrementalCompileStateRecorder incrementalCompileStateRecorder() {
        return incrementalCompileStateRecorder;
    }

    MainCompileSourceExecutor sourceExecutor() {
        return sourceExecutor;
    }

    private record ResolveDependencies(
            ResolveService resolveService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
    }

    private record OutputDependencies(
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildMetadataGenerator buildMetadataGenerator,
            BuildFingerprintService buildFingerprintService) {
    }

    private record GeneratedSourceDependencies(
            JdkChecker jdkDetector,
            OpenApiGeneratedSourceService openApiGeneratedSourceService,
            ProtobufGeneratedSourceService protobufGeneratedSourceService,
            SpringBootAotGenerationService springBootAotGenerationService,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            MainCompileSourceExecutor sourceExecutor) {
    }
}
