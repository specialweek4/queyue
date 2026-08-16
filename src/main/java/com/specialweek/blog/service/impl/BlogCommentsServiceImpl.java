package com.specialweek.blog.service.impl;

import com.specialweek.blog.domain.BlogComments;
import com.specialweek.blog.mapper.BlogCommentsMapper;
import com.specialweek.blog.service.IBlogCommentsService;
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
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
