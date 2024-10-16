package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Resource
    private IBlogService blogService;

    /**
     * 查询博客详情
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在!");
        }
        //2.查询blog有关用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlokLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询博客是否被点赞
     * @param blog
     */
    private void isBlokLiked(Blog blog) {
        //1.获取登入用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            //用户未登入
            return;
        }
        Long userId = user.getId();
        //2.判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMember));
        blog.setIsLike(score != null);
    }

    /**
     * 查询所有热点博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            //查询用户的相关信息
            this.queryBlogUser(blog);
            //查询用户是否被点赞
            this.isBlokLiked(blog);
        });

        return Result.ok(records);
    }



    /**
     * 点赞博客
     * @param id
     * @return
     */
//    @Override
//    public Result likeBlog(Long id) {
//        //1.获取登入用户
//        Long userId = UserHolder.getUser().getId();
//        //2.判断当前用户是否已经点赞
//        String key = BLOG_LIKED_KEY + id;
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        //3.如果未点赞,可以点赞
//        if (BooleanUtil.isFalse(isMember)){
//            //3.1数据库点赞数+1
//            boolean isSuccess = update().setSql("liked = liked + 1")
//                    .eq("id", id)
//                    .update();
//            if (isSuccess) {
//                //3.保存到Redis的Set集合
//                stringRedisTemplate
//                        .opsForSet()
//                        .add(key, userId.toString());
//            }
//        } else {
//            //4.如果已点赞，取消点赞
//            //4.1数据库点赞数-1
//            boolean isSuccess = update().setSql("liked = liked - 1")
//                    .eq("id", id)
//                    .update();
//            //4.2把用户从redis的set集合移出
//            if (isSuccess){
//                stringRedisTemplate.opsForSet().remove(key, userId.toString());
//            }
//        }
//        return Result.ok();
//    }

    /**
     * 点赞博客(引入排行榜)
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取登入用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果未点赞,可以点赞
        if (score == null){
            //3.1数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1")
                    .eq("id", id)
                    .update();
            if (isSuccess) {
                //3.保存到Redis的Sort_set集合 zadd key value score
                stringRedisTemplate
                        .opsForZSet()
                        .add(key, userId.toString(),System.currentTimeMillis());
            }
        } else {
            //4.如果已点赞，取消点赞
            //4.1数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();
            //4.2把用户从redis的set集合移出
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询top5用户
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top点赞用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //2.解析出其中的用户id
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        //3.根据用户id查询用户
        List<UserDTO> userDTOS = userService.query().in("id",ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(
                        user -> BeanUtil.copyProperties(user, UserDTO.class)
                ).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 博主发布博客后，把博客发给自己的粉丝
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登入用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query()
                .eq("follow_user_id", user.getId()).list();

        for (Follow follow : follows) {
            //4.1 获取粉丝Id
            Long userId = follow.getUserId();
            //4.2 推送
            String key = FEED_KEY + userId;
            Boolean add = stringRedisTemplate.opsForZSet()
                    .add(key, blog.getId().toString(), System.currentTimeMillis());
            //5 返回Id
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2.查询收件箱 ZREVRANGEBYSCORE key MAX MIN LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //4.解析数据:blogId、minTime(时间戳)、offset
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;   //2
        int os = 1;     //返回此次循环中，末尾有多少个重复的数据
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //5 4 4 2 2
            //4.1获取id
            ids.add(Long.valueOf(typedTuple.getValue()));   //最后要拿来查询博客列表
            //4.2获取分数(时间戳)
            long time = typedTuple.getScore().longValue();  //每次覆盖，拿到最后一个,就是上一次的最大时间
            if (time == minTime){
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset;
        //5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        for (Blog blog : blogs) {
            //5.1查询blog有关的用户
            queryBlogUser(blog);
            //5.2查询blog是否被点赞
            isBlokLiked(blog);
        }

        //6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }


    /**
     * 通过blog里面的userId,查询用户的头像和昵称，设置到blog里面
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
