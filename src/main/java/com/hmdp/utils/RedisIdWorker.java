package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于Redis实现的ID生成器
 */
@Component
public class RedisIdWorker {

    // 序列号位数
    private static final int COUNT_BITS = 32;

    // 起始时间戳 (2022-01-01 00:00:00 UTC)
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    // Redis操作模板
    private StringRedisTemplate redisTemplate;

    /**
     * 构造函数，注入StringRedisTemplate
     *
     * @param redisTemplate Redis模板
     */
    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成下一个ID
     *
     * @param keyPrefix ID前缀
     * @return 下一个ID
     */
    public long nextId(String keyPrefix){
        // 1. 生成当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成日期字符串和序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接时间戳和序列号，并返回
        return timestamp << COUNT_BITS | increment;
    }
}
