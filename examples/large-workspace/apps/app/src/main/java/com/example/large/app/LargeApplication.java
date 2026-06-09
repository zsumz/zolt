package com.example.large.app;

import com.example.large.service.LargeService;
import org.apache.commons.io.FilenameUtils;

public final class LargeApplication {
    private LargeApplication() {
    }

    public static void main(String[] args) {
        System.out.println(message());
    }

    public static String message() {
        return FilenameUtils.getBaseName("large-workspace.txt") + ":" + new LargeService().describe();
    }
}
