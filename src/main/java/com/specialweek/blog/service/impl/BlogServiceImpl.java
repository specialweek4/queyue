package com.specialweek.blog.service.impl;

import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.web.Result;
import org.springframework.stereotype.Service;

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
}
