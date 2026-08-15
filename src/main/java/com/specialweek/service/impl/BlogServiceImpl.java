package com.specialweek.service.impl;

import com.specialweek.entity.Blog;
import com.specialweek.mapper.BlogMapper;
import com.specialweek.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

}
