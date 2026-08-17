package com.specialweek.blog.api;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.user.api.dto.UserDTO;
import com.specialweek.blog.domain.Blog;
import com.specialweek.user.domain.User;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.user.service.IUserService;
import com.specialweek.common.util.SystemConstants;
import com.specialweek.user.util.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private OssStorageService ossStorageService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        if (StrUtil.isBlank(blog.getContentObjectKey())) {
            return Result.fail("请先上传正文");
        }
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        blog.setStatus(1);
        blog.setPublishTime(LocalDateTime.now());
        fillDescription(blog);
        blogService.save(blog);
        return Result.ok(blog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        blogService.update()
                .setSql("liked = liked + 1").eq("id", id).update();
        return Result.ok();
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .orderByDesc("update_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 热门主页
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        //TODO增加缓存策略
        Page<Blog> page = blogService.query()
                .eq("status", 1)                                  // ← 对外只给已发布
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            User u = userService.getById(blog.getUserId());
            blog.setName(u.getNickName());
            blog.setIcon(u.getIcon());
        });
        return Result.ok(records);
    }

    /**
     * 看别人的主页
     * @param id
     * @param current
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogOfUser(@RequestParam("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .eq("status", 1)                                  // ← 对外只给已发布
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 保存草稿
     * @param blog
     * @return
     */
    @PostMapping("/draft")
    public Result saveDraft(@RequestBody Blog blog) {
        UserDTO user = UserHolder.getUser();
        //这里的设计是如果是创建草稿就不会传blogid，就会直接创建新的，有id的话就看看是不是自己的
        if (blog.getId() != null) {
            Blog old = blogService.getById(blog.getId());
            if (old == null || !old.getUserId().equals(user.getId())) {
                return Result.fail("无权操作该草稿");
            }
        }
        blog.setUserId(user.getId());
        blog.setStatus(0);
        fillDescription(blog);                     // ← description 操作
        blogService.saveOrUpdate(blog);
        return Result.ok(blog.getId());
    }

    /**
     * 草稿转发布
     * @param id
     * @return
     */
    @PutMapping("/{id}/publish")
    public Result publish(@PathVariable("id") Long id) {
        UserDTO user = UserHolder.getUser();
        Blog blog = blogService.getById(id);
        if (blog == null || !blog.getUserId().equals(user.getId())) {
            return Result.fail("无权操作");
        }
        blog.setStatus(1);
        blog.setPublishTime(LocalDateTime.now());
        blogService.updateById(blog);
        return Result.ok();
    }

    /**
     * 补充摘要
     * @param blog
     */
    private void fillDescription(Blog blog) {
        if (StrUtil.isBlank(blog.getDescription()) && StrUtil.isNotBlank(blog.getContentText())) {
            blog.setDescription(StrUtil.subPre(blog.getContentText().trim(), 50));
        }
    }

    /**
     * 根据id获取笔记详情
     * @param id
     * @return
     */
    @GetMapping("/detail/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        Blog blog = blogService.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        UserDTO user = UserHolder.getUser();
        // 草稿只有作者可见（放行游客后 user 可能为 null，需判空防 NPE）
        if (blog.getStatus() != 1 && (user == null || !user.getId().equals(blog.getUserId()))) {
            return Result.fail("笔记不存在");
        }
        // 作者信息回填
        User author = userService.getById(blog.getUserId());
        blog.setName(author.getNickName());
        blog.setIcon(author.getIcon());
        if(user == null){
            blog.setIsLike(false);
            blog.setFollowed(false);
        }
        // 正文：把 objectKey 拼成可访问 URL
        if (StrUtil.isNotBlank(blog.getContentObjectKey())) {
            blog.setContentUrl(ossStorageService.publicUrl(blog.getContentObjectKey()));
        }
        return Result.ok(blog);
    }
}
