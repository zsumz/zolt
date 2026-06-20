package com.zolt.quality;

import com.zolt.workspace.WorkspaceSelectionRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

abstract class QualityCheckServiceTestSupport {
    protected static QualityCheckReport check(
            Path projectDir,
            Path cacheDir,
            Map<String, String> environment,
            QualityCheckContext context,
            List<String> checks) {
        QualityCheckService service = new QualityCheckService(environment::get);
        return service.check(new QualityCheckRequest(
                projectDir,
                cacheDir,
                false,
                false,
                checks,
                context,
                null,
                null,
                false,
                false,
                false,
                WorkspaceSelectionRequest.defaults()));
    }
}
