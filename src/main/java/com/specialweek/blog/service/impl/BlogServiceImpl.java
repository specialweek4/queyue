package com.specialweek.blog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.service.BlogDetailService;
import com.specialweek.blog.service.BlogFeedService;
import com.specialweek.blog.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.util.SystemConstants;
import com.specialweek.common.web.Result;
import com.specialweek.common.web.ScrollResult;
import com.specialweek.user.domain.User;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private BlogFeedService blogFeedService;
    @Resource
    private BlogDetailService blogDetailService;

    @Override
    public Result delete(long userid, long id) {
        Blog blog = getById(id);
        if(blog == null || blog.getUserId() != userid) return Result.fail("删除错误");
        blogFeedService.invalidateCache(id);
        blogDetailService.invalidate(id);
        blog.setStatus(2);
        saveOrUpdate(blog);
        blogFeedService.invalidateCache(id);
        blogDetailService.invalidate(id);
        return Result.ok("删除成功，该笔记放进回收站");
    }

    @Override
    public ScrollResult queryFeedOfFollow(long userId, long lastIdMillis, int offset) {
        LocalDateTime maxTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastIdMillis), ZoneId.systemDefault());
        List<Blog> records = baseMapper.selectFeedOfFollow(
                userId, maxTime, Math.max(0, offset), SystemConstants.MAX_PAGE_SIZE);

        ScrollResult result = new ScrollResult();
        if (records == null || records.isEmpty()) {
            result.setList(List.of());
            return result;
        }

        Blog last = records.get(records.size() - 1);
        long minTime = last.getPublishTime() == null
                ? lastIdMillis
                : last.getPublishTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        int sameCount = 1;
        for (int i = records.size() - 2; i >= 0; i--) {
            Blog b = records.get(i);
            if (b.getPublishTime() != null && b.getPublishTime().equals(last.getPublishTime())) {
                sameCount++;
            } else {
                break;
            }
        }
        result.setList(records);
        result.setMinTime(minTime);
        result.setOffset(sameCount);
        return result;
    }

    @Override
    public List<Blog> deletelist(long userId, Integer current, int maxPageSize) {
        Page<Blog> page = query()
                .eq("user_id", userId)
                .eq("status", 2)
                .orderByDesc("update_time")
                .page(new Page<>(current, maxPageSize));
        return page.getRecords();
    }

    @Override
    public Result blogDeleteForever(Long blogId, long userId) {
        if(blogId == null || blogId <= 0){
            return Result.fail("blogId不合法");
        }
        Blog blog = getById(blogId);
        if(blog == null){
            return Result.fail("blog不存在");
        }

        if(!Objects.equals(blog.getUserId(), userId) || !Integer.valueOf(2).equals(blog.getStatus())){
            return Result.fail("错误，请稍后重试");
        }

        //ToDO删除相关的缓存和Reids计数

        Boolean dbChanged = remove(
                Wrappers.<Blog>lambdaQuery()
                        .eq(Blog::getId, blog)
                        .eq(Blog::getUserId, userId)
                        .eq(Blog::getStatus, 2)
        );

        if(!dbChanged){
            throw new IllegalStateException("删除失败请稍后再试");
        }

        return Result.ok("博客已永久删除");
    }

    @Override
    public Result revive(Long blogId, long userId) {
        if(blogId == null || blogId <= 0){
            return Result.fail("blogId不合法");
        }

        Blog blog = getById(blogId);
        if(blog == null){
            return Result.fail("blog不存在");
        }

        if(!Objects.equals(blog.getUserId(), userId) || !Integer.valueOf(2).equals(blog.getStatus())){
            return Result.fail("错误，请稍后重试");
        }
        blog.setStatus(0);
        if(!saveOrUpdate(blog)){
            throw new IllegalStateException("错误,请稍后重试，或联系工作人员");
        };
        return Result.ok();
    }
}
