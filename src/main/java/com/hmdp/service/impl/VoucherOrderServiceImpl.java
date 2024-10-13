package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 非分布式解决
     * @param voucherId
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断是否在规定时间里
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        synchronized (userId.toString().intern()) {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
//    }

    /**
     * 旧版本的,未引入复杂的阻塞队列
     * @param
     * @return
     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId){
//        //一人一单,判断是否存在
//        Long userId = UserHolder.getUser().getId();
//
//            Integer count = query().eq("user_id", userId)
//                    .eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                //用户已经购买过
//                return Result.fail("用户已经购买过!");
//            }
//
//
//            //4.扣减库存
//            //1.原始:会有超卖
////        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
////                        ("voucher_id", voucherId).
////                update();
//            //2.不会有超卖,但是会有很多失败,如果有50张票，同时进入30人，但只会有1人成功，其实他们30人都可以成功
////        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
////                        ("voucher_id", voucherId).
////                eq("stock", voucher.getStock()).
////                update();
//
//            //这种方法便正常了
//            boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
//                            ("voucher_id", voucherId).
//                    gt("stock", 0).
//                    update();
//
//
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//
//            //5.创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            //6.返回订单id
//            //6.1 订单id
//            long orderId = redisIdWorker.nextId("order");   //使用redis生成全局Id
//            //6.2 用户id(登录拦截器里有)
//
//            //6.3 代金券id
//            voucherOrder.setVoucherId(voucherId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setId(orderId);
//            save(voucherOrder);
//            return Result.ok(orderId);
//        }

    /**
     * 分布式解决
     * @param
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断是否在规定时间里
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
//
//        // 获取锁
//        boolean isLock = lock.tryLock(1200l);
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//            // 或者重试
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//    }

//    /**
//     * 误删解决(线程标识用UUID标识)
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断是否在规定时间里
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        // 获取锁
//        boolean isLock = lock.tryLock();
////        boolean isLock = lock.tryLock(1200l);
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//            // 或者重试
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        //一人一单,判断是否存在
        Long userId = voucherOrder.getUserId();

        Integer count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            //用户已经购买过
            log.error("用户已经购买过了");
            return;
        }




        //这种方法便正常了
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
                        ("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).
                update();


        if (!success) {
            // 扣减失败
            log.error("库存不足");
            return ;
        }
        long orderId = redisIdWorker.nextId("order");   //使用redis生成全局Id


        save(voucherOrder);

    }



//使用redis代替
//    private BlockingQueue<VoucherOrder> orderTasks
//            = new ArrayBlockingQueue<>(1024 * 1024);
// 用于线程池处理的任务
// 当初始化完毕后，就会去从对列中去拿信息
//private class VoucherOrderHandler implements Runnable{
//
//    @Override
//    public void run() {
//        while (true){
//            try {
//                //1.获取队列中的订单信息（take:获取队列中的第一个元素，没有的话就卡在这）
//                VoucherOrder voucherOrder = orderTasks.take();
//
//                //2.创建订单
//                handleVoucherOrder(voucherOrder);
//            } catch (Exception e){
//                log.error("处理订单异常",e);
//            }
//        }
//
//    }
//}

    //不再从阻塞队列中获取，而是从消息队列中去获取
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1.获取消息队列中的订单信息 XREAD GROUP g1 c1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    //ReadOffset.lastConsumed():最近一个未消费的信息
                    //ReadOffset.lastest():最新的消息
                    //2.判断消息是否成功
                    if (list == null || list.isEmpty()){
                        //2.1如果失败,说明没有消息,继续下一次循环
                        continue;
                    }

                    //3.解析订单中的消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);//true:出错是否忽略
                    //3.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);

                    //4.ACK确认 SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
                } catch (Exception e){
                    log.error("处理订单异常",e);
                    try {
                        handlePendingList();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

        }

        private void handlePendingList() throws InterruptedException {

            while (true){
                try {
                    //1.获取pending-list中的订单信息 XREAD GROUP g1 c1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0")));
                    //ReadOffset.lastConsumed():最近一个未消费的信息
                    //ReadOffset.lastest():最新的消息
                    //2.判断消息是否成功
                    if (list == null || list.isEmpty()){
                        //2.1如果失败,说明pending-list没有消息,结束循环
                        break;
                    }

                    //3.解析订单中的消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);//true:出错是否忽略
                    //3.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);

                    //4.ACK确认 SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
                } catch (Exception e){
                    log.error("处理pending-list订单异常",e);
                    Thread.sleep(50);
                }
            }
        }
    }

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR
            = Executors.newSingleThreadExecutor();

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }



    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户,这里不能从线程池中去取，现在不是主线程
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁
        boolean isLock = redisLock.tryLock();
        //4.判断是否获得成功
        if (!isLock){
            log.error("不允许重复下单");
            return;
        }
        try {
            //由于spring的事务是放在threadLocal中，此时是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            redisLock.unlock();
        }
    }


    private IVoucherOrderService proxy;

    /**
     * 使用Lua脚本解决
     * @param voucherId
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        Long orderId = redisIdWorker.nextId("order");
//
//        // 1.执行lua脚本
//        Long result = redisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString(),
//                String.valueOf(orderId));
//        //判断结果是否为0 (1):库存不足,(2):重复下单
//        int r = result.intValue();
//        // 2.判断结果是否为0
//        if (r != 0) {
//            // 2.1.不为0 ，代表没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 2.3.订单id
//        voucherOrder.setId(orderId);
//        // 2.4.用户id
//        voucherOrder.setUserId(userId);
//        // 2.5.代金券id
//        voucherOrder.setVoucherId(voucherId);
//
//        //2.6放入阻塞队列
//        orderTasks.add(voucherOrder);
//
//        //3.获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //放到lua脚本
//        //返回订单Id
//        return Result.ok(orderId);
//    }

    /**
     * 引入消息队列
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");

        // 1.执行lua脚本
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),
                String.valueOf(orderId));
        //判断结果是否为0 (1):库存不足,(2):重复下单
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //放到lua脚本
        //返回订单Id
        return Result.ok(orderId);
    }
}
