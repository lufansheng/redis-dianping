package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;
    /**
     * 是否关注followUserId这个人
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登入用户
        Long userId = UserHolder.getUser().getId();
        //查询userId是否关注了foolUserId
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    /**
     * 关注或取关followUserId这个人
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取登入用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY + userId;
        if (isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            //关注成功后,以模块follows + 当前登入用户为key
            //以up主的id为value保存到redis中,这样就可以知道
            //userId这个用户关注了哪些up主
            if (isSuccess){
                stringRedisTemplate.opsForSet()
                        .add(key,followUserId.toString());
            }
        } else {
            //取关,删除数据库对应的信息
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess){
                //同时也要把redis离的集合删掉
                stringRedisTemplate.opsForSet().remove(
                        key,followUserId.toString()
                );
            }
        }
        return Result.ok();
    }

    @Override
    public Result followConmons(Long followUserId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY + userId;

        //2.求交集
        String key2 = FOLLOWS_KEY + followUserId;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
