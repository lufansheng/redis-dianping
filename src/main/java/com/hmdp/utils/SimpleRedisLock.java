package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    private String name;    //锁的名称(有关业务)
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 定义一个静态常量 UNLOCK_SCRIPT 用于执行 Redis 分布式锁的解锁操作
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        // 创建 DefaultRedisScript 实例并设置其脚本资源位置为类路径下的 "unlock.lua" 文件
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 指定执行脚本后返回的结果类型为 Long
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //value要加上线程的标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        long threadId = Thread.currentThread().getId();

        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,
                threadId
                , timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//
//        //获取锁钟的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        //判断是否一致
//        if (threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
        @Override
        public void unlock() {
            //調用lua脚本
            stringRedisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(KEY_PREFIX + name),
                    ID_PREFIX + Thread.currentThread().getId()
            );
        }
}
