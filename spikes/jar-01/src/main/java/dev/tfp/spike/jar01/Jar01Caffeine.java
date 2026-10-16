package dev.tfp.spike.jar01;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** Kept in its own class so a missing library shows up as one FAIL, not a broken plugin. */
final class Jar01Caffeine {
    private Jar01Caffeine() {}

    static String probe() {
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(100).build();
        cache.put("k", "v");
        String got = cache.getIfPresent("k");
        if (!"v".equals(got)) {
            throw new IllegalStateException("cache get returned " + got);
        }
        String src = Caffeine.class.getProtectionDomain().getCodeSource().getLocation().toString();
        return "put/get ok, class from " + src.substring(Math.max(0, src.length() - 60));
    }
}
