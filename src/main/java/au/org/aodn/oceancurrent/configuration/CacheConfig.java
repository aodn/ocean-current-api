package au.org.aodn.oceancurrent.configuration;

import au.org.aodn.oceancurrent.constant.CacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(12, TimeUnit.HOURS));

        cacheManager.setCacheNames(List.of(
                CacheNames.IMAGE_LIST,
                CacheNames.CURRENT_METERS_PLOT_LIST,
                CacheNames.LATEST_FILES,
                CacheNames.BUOY_TIME_SERIES
        ));

        return cacheManager;
    }
}
