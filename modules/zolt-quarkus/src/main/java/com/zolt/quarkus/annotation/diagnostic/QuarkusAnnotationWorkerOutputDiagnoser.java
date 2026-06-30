package com.zolt.quarkus.annotation.diagnostic;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationJvmRunner;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequest;

public final class QuarkusAnnotationWorkerOutputDiagnoser {
    private final QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic;

    public QuarkusAnnotationWorkerOutputDiagnoser() {
        this(new QuarkusAnnotationClasspathSplitDiagnostic());
    }

    public QuarkusAnnotationWorkerOutputDiagnoser(
            QuarkusAnnotationClasspathSplitDiagnostic classpathSplitDiagnostic) {
        if (classpathSplitDiagnostic == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation worker classpath split diagnostic is required.");
        }
        this.classpathSplitDiagnostic = classpathSplitDiagnostic;
    }

    public String diagnose(QuarkusAnnotationLaunchRequest request, QuarkusAnnotationJvmRunner.Result result) {
        if (result.exitCode() == 0 || result.output() == null || result.output().isBlank()) {
            return result.output();
        }
        if (!buildChainBuilderClassloaderSplit(result.output())) {
            if (missingBuilderApi(result.output())) {
                return "error: Quarkus annotation test bootstrap could not load the Quarkus builder API from "
                        + "the annotation runner side. Zolt reached the dedicated @QuarkusTest runner path, "
                        + "but this fixture still needs a classloader arrangement where Quarkus JUnit can see "
                        + "builder API types without loading deployment-owned builder classes from the wrong side. "
                        + classpathSplitDiagnostic.describeMissingBuilderApi(request)
                        + "\n"
                        + result.output().stripTrailing()
                        + "\n";
            }
            if (testConfigClassloaderMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus test configuration initialization, "
                    + "then hit a classloader type mismatch inside FacadeClassLoader. The builder API ownership hint "
                    + "moved this descriptor-enabled probe past the BuildChainBuilder split, but Zolt still needs "
                    + "to align Quarkus test config class ownership before @QuarkusTest can be enabled. "
                    + "Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testConfigLauncherSessionMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached JUnit launcher-session cleanup, then hit "
                    + "a Quarkus test config resolver ownership mismatch. Zolt's test config parent-first hints "
                    + "moved this descriptor-enabled probe past FacadeClassLoader initialization, but the runner "
                    + "still needs to align QuarkusTestConfigProviderResolver and ConfigLauncherSession ownership "
                    + "before @QuarkusTest can be enabled. Keep using plain JUnit tests for now, or run "
                    + "`zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testHttpEndpointProviderSplit(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus application startup, then hit "
                    + "a runtime service-provider classloader split for TestHttpEndpointProvider. Running the "
                    + "annotation probe from the JVM classpath moved past the TestConfig mapping blocker, but "
                    + "Zolt still needs to align Quarkus runtime service loading before @QuarkusTest can be "
                    + "enabled. Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect "
                    + "blocked tests. "
                    + classpathSplitDiagnostic.describeRuntimeServiceProviderSplit(request)
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testScopeSetupProviderSplit(result.output())) {
                return "error: Quarkus annotation test bootstrap reached per-test scope setup, then hit "
                    + "a runtime service-provider classloader split for TestScopeSetup. Zolt can now produce "
                    + "Quarkus additional-bean build items for selected @QuarkusTest classes, but the runner "
                    + "still needs to align Arc test request-scope provider ownership before @QuarkusTest can "
                    + "be enabled. Keep using plain JUnit tests for now, or run `zolt quarkus test-plan` "
                    + "to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (applicationClassMissingDuringHttpRequest(result.output())) {
                return "error: Quarkus annotation test bootstrap reached REST Assured HTTP execution, then "
                    + "the running Quarkus application could not load an application class. Zolt moved this "
                    + "descriptor-enabled probe past Arc test bean registration and Arc test request-scope "
                    + "service loading, but the runner still needs to align application class visibility in "
                    + "the Quarkus runtime classloader before @QuarkusTest can be enabled. Keep using plain "
                    + "JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (configProducerVerifierMismatch(result.output())) {
                return "error: Quarkus annotation test bootstrap reached config-backed injection, then hit "
                    + "a SmallRye Config producer verifier mismatch. Zolt's supported direct "
                    + "@QuarkusTest fixture keeps SmallRye Config provider types parent-first while leaving "
                    + "the SmallRye CDI config producer runtime-owned; this failure means those ownership "
                    + "hints regressed or the project is using an unproven Quarkus/SmallRye classloading "
                    + "shape. Run `zolt quarkus test-plan` to inspect the current annotation-runner boundary."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testConfigMappingMissing(result.output())) {
                return "error: Quarkus annotation test bootstrap reached Quarkus JUnit execution through Zolt's "
                    + "programmatic runner and facade-loader context-classloader handoff, then hit a missing "
                    + "Quarkus TestConfig mapping. That handoff moved this descriptor-enabled probe past the "
                    + "runtime TestHttpEndpointProvider service-loading split, but Zolt still needs to align "
                    + "Quarkus test config mapping ownership before @QuarkusTest can be enabled. Keep using "
                    + "plain JUnit tests for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (profileTestClassBeanMissing(result.output())) {
                return "error: Quarkus annotation test bootstrap activated a test profile, restarted the "
                    + "Quarkus application, then Arc could not instantiate the profiled @QuarkusTest class "
                    + "as a CDI bean. Zolt can instantiate the public QuarkusTestProfile and can produce "
                    + "test-class bean customizer output for the selected class. Check the printed "
                    + "`producer.indexSelectedClasses`, `profileMatchesActiveProfile`, "
                    + "`additionalBeanProducerStep.produced`, `containsProfileVetoProcessor`, "
                    + "`containsProfileBeanVetoProcessor`, `containsArcRegisterBeans`, and "
                    + "`generatedArcBeanClasses` entries, then compare the early additional-bean producer, "
                    + "the test-class bean producer, the application-class predicate output directories, "
                    + "the generated Arc bytecode jar, and the Arc build graph to separate profile-veto "
                    + "mismatches, missing test-output visibility, and the remaining Arc bean-generation "
                    + "handoff before @TestProfile can be enabled. "
                    + "Keep profiles unsupported for now, or run `zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            if (testClassBeanMissing(result.output())) {
                return "error: Quarkus annotation test bootstrap started the Quarkus application, then Arc could "
                    + "not instantiate the selected @QuarkusTest class as a CDI bean. Zolt moved this "
                    + "descriptor-enabled probe past runtime service loading and can prove the enriched "
                    + "test-class index contains the selected class as a Quarkus build-chain test bean candidate, "
                    + "but the actual Quarkus test augmentation path still does not register it as an Arc bean. "
                    + "Zolt still needs to align TestClassBeanBuildItem production with Arc additional-bean "
                    + "registration under the descriptor-owned test application model. Keep using plain JUnit tests for now, or run "
                    + "`zolt quarkus test-plan` to inspect blocked tests."
                    + "\n"
                    + result.output().stripTrailing()
                    + "\n";
            }
            return result.output();
        }
        return "error: Quarkus annotation test bootstrap hit a Quarkus classloader split while loading "
                + "io.quarkus.builder.BuildChainBuilder. Zolt reached the dedicated @QuarkusTest runner path, "
                + "but this fixture still needs Quarkus deployment/runtime classloader ownership work before "
                + "Quarkus test annotations can be enabled. Keep using plain JUnit tests for now, or run "
                + "`zolt quarkus test-plan` to inspect blocked tests. "
                + classpathSplitDiagnostic.describe(request)
                + "\n"
                + result.output().stripTrailing()
                + "\n";
    }

    private static boolean buildChainBuilderClassloaderSplit(String output) {
        return output.contains("io.quarkus.test.junit")
                && output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                && output.contains("cannot be cast to class io.quarkus.builder.BuildChainBuilder");
    }

    private static boolean missingBuilderApi(String output) {
        return output.contains("io.quarkus.test.junit")
                && output.contains("NoClassDefFoundError: io/quarkus/builder/item/MultiBuildItem");
    }

    private static boolean testConfigClassloaderMismatch(String output) {
        return output.contains("io.quarkus.test.junit.classloading.FacadeClassLoader.initialiseTestConfig")
                && output.contains("java.lang.IllegalArgumentException: argument type mismatch");
    }

    private static boolean testConfigLauncherSessionMismatch(String output) {
        return output.contains("io.quarkus.test.config.ConfigLauncherSession.launcherSessionClosed")
                && output.contains("QuarkusTestConfigProviderResolver cannot be cast")
                && output.contains("io.quarkus.test.config.TestConfigProviderResolver");
    }

    private static boolean testConfigMappingMissing(String output) {
        return output.contains("SRCFG00027: Could not find a mapping for io.quarkus.deployment.dev.testing.TestConfig")
                && output.contains("io.quarkus.test.junit");
    }

    private static boolean testHttpEndpointProviderSplit(String output) {
        return output.contains("java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestHttpEndpointProvider")
                && output.contains("not a subtype");
    }

    private static boolean testScopeSetupProviderSplit(String output) {
        return output.contains("java.util.ServiceConfigurationError: io.quarkus.runtime.test.TestScopeSetup")
                && output.contains("not a subtype");
    }

    private static boolean applicationClassMissingDuringHttpRequest(String output) {
        return output.contains("HTTP/1.1 500 Internal Server Error")
                && output.contains("java.lang.NoClassDefFoundError")
                && output.contains("quarkusrestinvoker");
    }

    private static boolean configProducerVerifierMismatch(String output) {
        return output.contains("java.lang.VerifyError")
                && output.contains("ConfigProducer_ClientProxy.produceStringConfigProperty")
                && output.contains("io/smallrye/config/inject/ConfigProducer")
                && output.contains("Bad access to protected data");
    }

    private static boolean testClassBeanMissing(String output) {
        return output.contains("jakarta.enterprise.inject.UnsatisfiedResolutionException")
                && output.contains("No bean found for required type")
                && output.contains("io.quarkus.test.junit");
    }

    private static boolean profileTestClassBeanMissing(String output) {
        return testClassBeanMissing(output)
                && output.contains("Profile test activated.");
    }
}
