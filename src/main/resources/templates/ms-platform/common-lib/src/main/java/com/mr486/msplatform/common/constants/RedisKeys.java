package com.mr486.msplatform.common.constants;

public final class RedisKeys {
    public static final String BATCH_JOBS_ALL = "batch:jobs:all";

    public static String batchJob(String jobId) {
        return "batch:job:" + jobId;
    }

    public static String batchUserJobs(Long userId) {
        return "batch:user:" + userId + ":jobs";
    }

    public static String authBlacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    public static String authRefresh(String opaqueToken) {
        return "auth:refresh:" + opaqueToken;
    }

    private RedisKeys() {
    }
}
