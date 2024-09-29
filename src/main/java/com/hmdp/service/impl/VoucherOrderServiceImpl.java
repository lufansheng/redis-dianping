package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断是否在规定时间里
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //一人一单,判断是否存在
        Long userId = UserHolder.getUser().getId();

            Integer count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherId).count();
            if (count > 0) {
                //用户已经购买过
                return Result.fail("用户已经购买过!");
            }


            //4.扣减库存
            //1.原始:会有超卖
//        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
//                        ("voucher_id", voucherId).
//                update();
            //2.不会有超卖,但是会有很多失败,如果有50张票，同时进入30人，但只会有1人成功，其实他们30人都可以成功
//        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
//                        ("voucher_id", voucherId).
//                eq("stock", voucher.getStock()).
//                update();

            //这种方法便正常了
            boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq
                            ("voucher_id", voucherId).
                    gt("stock", 0).
                    update();


            if (!success) {
                return Result.fail("库存不足");
            }


            //5.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //6.返回订单id
            //6.1 订单id
            long orderId = redisIdWorker.nextId("order");   //使用redis生成全局Id
            //6.2 用户id(登录拦截器里有)

            //6.3 代金券id
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(userId);
            voucherOrder.setId(orderId);
            save(voucherOrder);
            return Result.ok(orderId);
        }

}
