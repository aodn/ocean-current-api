package au.org.aodn.oceancurrent.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationScheduler {
    private final CacheManager cacheManager;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Australia/Hobart")
    public void evictAllCachesAtMidnight() {
        log.info("Starting scheduled cache invalidation at 3 AM Hobart time");

        List<Cache> cachesToClear = cacheManager.getCacheNames().stream()
                .map(cacheManager::getCache)
                .filter(Objects::nonNull)
                .toList();

        cachesToClear.forEach(cache -> {
            log.info("Clearing cache: {}", cache.getName());
            cache.clear();
        });
        log.info("Completed scheduled cache invalidation. Cleared {} caches.", cachesToClear.size());
    }

}
