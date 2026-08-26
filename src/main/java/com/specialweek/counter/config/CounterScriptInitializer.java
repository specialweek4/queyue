package com.specialweek.counter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class CounterScriptInitializer implements ApplicationRunner {

    private final StringRedisTemplate redis;
    private final List<DefaultRedisScript<?>> scripts;

    public CounterScriptInitializer(
            StringRedisTemplate redis,
            DefaultRedisScript<List> counterReadScript,
            DefaultRedisScript<Long> counterDirtyConfirmScript) {
        this.redis = redis;
        this.scripts = List.of(counterReadScript, counterDirtyConfirmScript);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DefaultRedisScript<?> script : scripts) {
            try {
                String sha = script.getSha1();
                redis.execute((RedisCallback<Object>) connection -> {
                    connection.scriptingCommands().scriptLoad(
                            script.getScriptAsString().getBytes(StandardCharsets.UTF_8));
                    return null;
                });
                log.info("计数脚本已预加载: sha1={}", sha);
            } catch (RuntimeException e) {
                log.warn("计数脚本预加载失败，将在运行时自动补加载: {}", e.getMessage());
            }
        }
    }
}
