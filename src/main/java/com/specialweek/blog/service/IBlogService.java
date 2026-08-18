package com.specialweek.blog.service;

import com.specialweek.blog.domain.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.specialweek.common.web.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface IBlogService extends IService<Blog> {

    Result delete(long userid, long id);
}
