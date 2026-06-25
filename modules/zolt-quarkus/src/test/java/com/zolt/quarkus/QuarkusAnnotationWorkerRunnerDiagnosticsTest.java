package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QuarkusAnnotationWorkerRunnerDiagnosticsTest extends QuarkusAnnotationWorkerRunnerTestSupport {
    @Test
    void explainsBuildChainBuilderClassloaderSplit() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                java.lang.ClassCastException: class io.quarkus.builder.BuildChainBuilder cannot be cast to class io.quarkus.builder.BuildChainBuilder
                    at io.quarkus.test.junit.TestBuildChainFunction$1.accept(TestBuildChainFunction.java:51)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Quarkus annotation test bootstrap hit a Quarkus classloader split"));
        assertTrue(result.output().contains("dedicated @QuarkusTest runner path"));
        assertTrue(result.output().contains("deployment/runtime classloader ownership work"));
        assertTrue(result.output().contains("BuildChainBuilder cannot be cast"));
    }

    @Test
    void explainsMissingBuilderApiOnLauncherClasspath() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                Cause: java.lang.NoClassDefFoundError: io/quarkus/builder/item/MultiBuildItem
                    at io.quarkus.test.junit.TestBuildChainFunction.collectTestAnnotationItems(TestBuildChainFunction.java:185)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("could not load the Quarkus builder API"));
        assertTrue(result.output().contains("builder API types"));
        assertTrue(result.output().contains("quarkus-builder is absent from the annotation JVM launcher classpath"));
        assertTrue(result.output().contains("MultiBuildItem"));
    }

    @Test
    void explainsTestConfigClassloaderMismatch() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                java.lang.IllegalArgumentException: argument type mismatch
                    at io.quarkus.test.junit.classloading.FacadeClassLoader.initialiseTestConfig(FacadeClassLoader.java:625)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Quarkus test configuration initialization"));
        assertTrue(result.output().contains("classloader type mismatch"));
        assertTrue(result.output().contains("moved this descriptor-enabled probe past the BuildChainBuilder split"));
        assertTrue(result.output().contains("argument type mismatch"));
    }

    @Test
    void explainsTestConfigLauncherSessionMismatch() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult(255, """
                java.lang.ClassCastException: class io.quarkus.test.junit.classloading.QuarkusTestConfigProviderResolver cannot be cast to class io.quarkus.test.config.TestConfigProviderResolver
                    at io.quarkus.test.config.ConfigLauncherSession.launcherSessionClosed(ConfigLauncherSession.java:38)
                """);

        assertEquals(255, result.exitCode());
        assertTrue(result.output().contains("JUnit launcher-session cleanup"));
        assertTrue(result.output().contains("test config resolver ownership mismatch"));
        assertTrue(result.output().contains("moved this descriptor-enabled probe past FacadeClassLoader"));
        assertTrue(result.output().contains("QuarkusTestConfigProviderResolver cannot be cast"));
    }

    @Test
    void explainsMissingTestConfigMapping() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                org.junit.jupiter.engine.execution.ConditionEvaluationException: Failed to evaluate condition [io.quarkus.test.junit.QuarkusTestExtension]
                Caused by: java.util.NoSuchElementException: SRCFG00027: Could not find a mapping for io.quarkus.deployment.dev.testing.TestConfig
                    at io.quarkus.test.junit.AbstractJvmQuarkusTestExtension.evaluateExecutionCondition(AbstractJvmQuarkusTestExtension.java:164)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("programmatic runner and facade-loader context-classloader handoff"));
        assertTrue(result.output().contains("missing Quarkus TestConfig mapping"));
        assertTrue(result.output().contains("moved this descriptor-enabled probe past"));
        assertTrue(result.output().contains("TestHttpEndpointProvider service-loading split"));
        assertTrue(result.output().contains("SRCFG00027"));
    }

    @Test
    void explainsTestHttpEndpointProviderSplit() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                java.lang.RuntimeException: java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider: io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider not a subtype
                    at io.quarkus.test.junit.QuarkusTestExtension.throwBootFailureException(QuarkusTestExtension.java:672)
                Caused by: java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider: io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider not a subtype
                    at io.quarkus.runtime.test.TestHttpEndpointProvider.load(TestHttpEndpointProvider.java:17)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached Quarkus application startup"));
        assertTrue(result.output().contains("runtime service-provider classloader split"));
        assertTrue(result.output().contains("moved past the TestConfig mapping blocker"));
        assertTrue(result.output().contains("Classpath ownership:"));
        assertTrue(result.output().contains("TestHttpEndpointProvider service loading"));
        assertTrue(result.output().contains("programmatic JUnit launcher"));
        assertTrue(result.output().contains("ResteasyReactiveTestHttpProvider not a subtype"));
    }

    @Test
    void explainsMissingTestClassBean() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                java.lang.RuntimeException: java.lang.RuntimeException: Error running Quarkus test
                    at io.quarkus.test.junit.QuarkusTestExtension.throwBootFailureException(QuarkusTestExtension.java:672)
                Caused by: jakarta.enterprise.inject.UnsatisfiedResolutionException:
                No bean found for required type [class com.example.quarkus.HelloResourceQuarkusTest]
                    at io.quarkus.test.junit.QuarkusTestExtension.runExtensionMethod(QuarkusTestExtension.java:949)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("started the Quarkus application"));
        assertTrue(result.output().contains("Arc could not instantiate the selected @QuarkusTest class"));
        assertTrue(result.output().contains("Quarkus build-chain test bean candidate"));
        assertTrue(result.output().contains("actual Quarkus test augmentation path"));
        assertTrue(result.output().contains("Arc additional-bean registration"));
        assertTrue(result.output().contains("TestClassBeanBuildItem"));
        assertTrue(result.output().contains("No bean found for required type"));
    }

    @Test
    void explainsTestScopeSetupClassloaderSplit() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestScopeSetup: io.quarkus.arc.runtime.ArcTestRequestScopeProvider not a subtype
                    at java.base/java.util.ServiceLoader.fail(ServiceLoader.java:593)
                    at io.quarkus.test.common.TestScopeManager.<clinit>(TestScopeManager.java:14)
                    at io.quarkus.test.junit.QuarkusTestExtension.beforeEach(QuarkusTestExtension.java:410)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached per-test scope setup"));
        assertTrue(result.output().contains("runtime service-provider classloader split for TestScopeSetup"));
        assertTrue(result.output().contains("additional-bean build items"));
        assertTrue(result.output().contains("Arc test request-scope provider ownership"));
        assertTrue(result.output().contains("ArcTestRequestScopeProvider not a subtype"));
    }

    @Test
    void explainsMissingApplicationClassDuringHttpRequest() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                HTTP/1.1 500 Internal Server Error
                {
                    "details": "java.lang.NoClassDefFoundError: com/example/quarkus/HelloResource"
                }
                at com.example.quarkus.HelloResource$quarkusrestinvoker$hello.invoke(Unknown Source)
                java.lang.AssertionError: 1 expectation failed.
                Expected status code <200> but was <500>.
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached REST Assured HTTP execution"));
        assertTrue(result.output().contains("could not load an application class"));
        assertTrue(result.output().contains("application class visibility"));
        assertTrue(result.output().contains("NoClassDefFoundError"));
    }

    @Test
    void explainsConfigProducerVerifierMismatch() {
        QuarkusAnnotationWorkerRunner.Result result = diagnosticResult("""
                Caused by: java.lang.VerifyError: Bad access to protected data in invokevirtual
                Exception Details:
                  Location:
                    io/smallrye/config/inject/ConfigProducer_ClientProxy.produceStringConfigProperty(Ljakarta/enterprise/inject/spi/InjectionPoint;)Ljava/lang/String; @18: invokevirtual
                  Reason:
                    Type 'io/smallrye/config/inject/ConfigProducer' (current frame, stack[0]) is not assignable to 'io/smallrye/config/inject/ConfigProducer_ClientProxy'
                    at io.smallrye.config.inject.ConfigProducer_Bean.proxy(Unknown Source)
                    at com.example.quarkus.GreetingResource_Bean.create(Unknown Source)
                    at com.example.quarkus.GreetingResourceQuarkusTest_Bean.create(Unknown Source)
                """);

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("reached config-backed injection"));
        assertTrue(result.output().contains("SmallRye Config producer verifier mismatch"));
        assertTrue(result.output().contains("SmallRye Config provider types parent-first"));
        assertTrue(result.output().contains("SmallRye CDI config producer runtime-owned"));
        assertTrue(result.output().contains("ConfigProducer_ClientProxy.produceStringConfigProperty"));
    }
}
