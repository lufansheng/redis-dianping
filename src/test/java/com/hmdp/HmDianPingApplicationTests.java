package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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
        service.saveShop2Redis(2L, 10L);
        service.saveShop2Redis(3L, 10L);
        service.saveShop2Redis(4L, 10L);
        service.saveShop2Redis(5L, 10L);
        service.saveShop2Redis(6L, 10L);
        service.saveShop2Redis(7L, 10L);
        service.saveShop2Redis(8L, 10L);
        service.saveShop2Redis(9L, 10L);
        service.saveShop2Redis(10L, 10L);
        service.saveShop2Redis(11L, 10L);
        service.saveShop2Redis(12L, 10L);
        service.saveShop2Redis(13L, 10L);
        service.saveShop2Redis(14L, 10L);
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


    @Autowired
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将店铺信息按照类型ID进行区分,导入redis里,下次查就快了
     */
    @Test
    void loadShopData() {
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2获取通类型的店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.写入redis GEOADD key 经度 维度 member     这么写太慢，换一种方式，直接构造一个location  geoadd key locations
            for (Shop shop : value) {

//                stringRedisTemplate.opsForGeo().add(key,
//                new Point(shop.getX(),shop.getY()),shop.getId().toString()                不选择这么写，这么写要多次操作redis
//                );
                //将shop的信息放到location里
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }

            //拿到外面,操作一次即可
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

    @Test
    void testHyperLogLog(){
        // 准备数组，装用户数据
        String[] users = new String[1000];
        // 数组角标
        int index = 0;
        for(int i = 1;i <= 1000000;i++){
            //赋值
            users[index++] = "user_" + i;
            if (i % 1000 == 0){
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1",users);
            }
        }
        //统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }
}
