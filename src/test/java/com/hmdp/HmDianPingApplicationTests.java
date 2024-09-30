package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * 测试类，用于执行各种功能测试
 */
@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl service;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 创建一个固定大小的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试ID生成器的性能
     * 使用300个线程并发生成订单ID
     */
    @Test
    void testIdWorker() throws InterruptedException {
        // 创建计数器，初始值为300
        CountDownLatch latch = new CountDownLatch(300);

        // 定义任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown(); // 计数器减一
            //只有这里的300各执行完了，才会继续执行下面的代码
        };

        // 记录开始时间
        long begin = System.currentTimeMillis();

        // 提交300个任务到线程池
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有任务完成
        latch.await();

        // 记录结束时间
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    /**
     * 测试将店铺信息保存到Redis中
     */
    @Test
    void testSaveShop() throws InterruptedException {
        service.saveShop2Redis(1L, 10L);
    }

    /**
     * 测试将店铺信息保存到Redis中，并设置过期时间
     */
    @Test
    void testSaveShop2() throws InterruptedException {
        // 获取店铺信息
        Shop shop = service.getById(1L);

        // 将店铺信息保存到Redis中，并设置过期时间为10秒
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }
}
