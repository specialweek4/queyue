package com.specialweek.service.impl;

import com.specialweek.entity.BlogComments;
import com.specialweek.mapper.BlogCommentsMapper;
import com.specialweek.service.IBlogCommentsService;
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
