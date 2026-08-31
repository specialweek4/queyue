package com.specialweek.blog.service.impl;

import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.util.SystemConstants;
import com.specialweek.common.web.Result;
import com.specialweek.common.web.ScrollResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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

    @Override
    public Result delete(long userid, long id) {
        Blog blog = getById(id);
        if(blog == null || blog.getUserId() != userid) return Result.fail("删除错误");
        blog.setStatus(2);
        saveOrUpdate(blog);
        return Result.ok("删除成功，该笔记放进回收站，7天内可恢复");
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

        Blog last = records.get(records.size() - 1);        long minTime = last.getPublishTime() == null
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
}
