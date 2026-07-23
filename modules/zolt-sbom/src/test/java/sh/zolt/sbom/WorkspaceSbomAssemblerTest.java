package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.ZoltLockfile;

final class WorkspaceSbomAssemblerTest extends SbomTestSupport {
    private final WorkspaceSbomAssembler assembler = new WorkspaceSbomAssembler();
    private final CycloneDxSbomWriter writer = new CycloneDxSbomWriter();

    @Test
    void aggregatesWorkspaceIntoOneBomWithMemberAndExternalEdges() {
        List<SbomWorkspaceMember> members = List.of(
                member("apps/app", "com.example", "app", "1.0.0"),
                member("modules/lib-a", "com.example", "lib-a", "1.0.0"));
        ZoltLockfile lockfile = lockfile(
                Optional.of("sha256:ws-fingerprint"),
                workspacePackage("com.example", "lib-a", "1.0.0", "modules/lib-a", List.of("apps/app")),
                externalWithMembers("org.ext", "ext-lib", "2.0.0", DependencyScope.COMPILE, SHA_A,
                        List.of(), List.of("modules/lib-a")));

        SbomModel model = assembler.assemble(
                "demo-ws", members, lockfile, SbomScopeSelection.requiredOnly(),
                Optional.empty(), TOOL_VERSION, LicenseIndex.empty());

        assertEquals("""
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.5",
                  "serialNumber": "urn:uuid:c889683b-abc6-55ed-b65f-271a2ffb6856",
                  "version": 1,
                  "metadata": {
                    "tools": [
                      {
                        "name": "zolt",
                        "version": "0.1.0-TEST"
                      }
                    ],
                    "component": {
                      "type": "application",
                      "bom-ref": "workspace:demo-ws",
                      "name": "demo-ws"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:maven/com.example/app@1.0.0?type=jar",
                      "group": "com.example",
                      "name": "app",
                      "version": "1.0.0",
                      "purl": "pkg:maven/com.example/app@1.0.0?type=jar",
                      "scope": "required"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:maven/com.example/lib-a@1.0.0?type=jar",
                      "group": "com.example",
                      "name": "lib-a",
                      "version": "1.0.0",
                      "purl": "pkg:maven/com.example/lib-a@1.0.0?type=jar",
                      "scope": "required"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:maven/org.ext/ext-lib@2.0.0?type=jar",
                      "group": "org.ext",
                      "name": "ext-lib",
                      "version": "2.0.0",
                      "purl": "pkg:maven/org.ext/ext-lib@2.0.0?type=jar",
                      "scope": "required",
                      "hashes": [
                        {
                          "alg": "SHA-256",
                          "content": "1111111111111111111111111111111111111111111111111111111111111111"
                        }
                      ]
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:maven/com.example/app@1.0.0?type=jar",
                      "dependsOn": ["pkg:maven/com.example/lib-a@1.0.0?type=jar"]
                    },
                    {
                      "ref": "pkg:maven/com.example/lib-a@1.0.0?type=jar",
                      "dependsOn": ["pkg:maven/org.ext/ext-lib@2.0.0?type=jar"]
                    },
                    {
                      "ref": "pkg:maven/org.ext/ext-lib@2.0.0?type=jar",
                      "dependsOn": []
                    },
                    {
                      "ref": "workspace:demo-ws",
                      "dependsOn": ["pkg:maven/com.example/app@1.0.0?type=jar", "pkg:maven/com.example/lib-a@1.0.0?type=jar"]
                    }
                  ]
                }
                """, writer.write(model));
    }
}
