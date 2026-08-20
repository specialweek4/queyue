package com.specialweek.blog.api;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.util.SystemConstants;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.user.domain.User;
import com.specialweek.user.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
    public Result saveBlog(@RequestBody Blog blog, @AuthenticationPrincipal Jwt jwt) {
        if (StrUtil.isBlank(blog.getContentObjectKey())) {
            return Result.fail("请先上传正文");
        }
        long userId = Long.parseLong(jwt.getSubject());
        blog.setUserId(userId);
        blog.setStatus(1);
        blog.setPublishTime(LocalDateTime.now());
        fillDescription(blog);
        blogService.save(blog);
        return Result.ok(blog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        blogService.update()
                .setSql("liked = liked + 1").eq("id", id).update();
        return Result.ok();
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current,
                              @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        Page<Blog> page = blogService.query()
                .eq("user_id", userId)
                .orderByDesc("update_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .eq("status", 1)
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

    @GetMapping("/of/user")
    public Result queryBlogOfUser(@RequestParam("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .eq("status", 1)
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @PostMapping("/draft")
    public Result saveDraft(@RequestBody Blog blog, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        if (blog.getId() != null) {
            Blog old = blogService.getById(blog.getId());
            if (old == null || !old.getUserId().equals(userId)) {
                return Result.fail("无权操作该草稿");
            }
        }
        blog.setUserId(userId);
        blog.setStatus(0);
        fillDescription(blog);
        if (blog.getId() == null) {
            blogService.save(blog);
        } else {
            blogService.updateById(blog);
        }
        return Result.ok(blog.getId());
    }

    @PutMapping("/{id}/publish")
    public Result publish(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        Blog blog = blogService.getById(id);
        if (blog == null || !blog.getUserId().equals(userId)) {
            return Result.fail("无权操作");
        }
        blog.setStatus(1);
        blog.setPublishTime(LocalDateTime.now());
        blogService.updateById(blog);
        return Result.ok();
    }

    private void fillDescription(Blog blog) {
        if (StrUtil.isBlank(blog.getDescription()) && StrUtil.isNotBlank(blog.getContentText())) {
            blog.setDescription(StrUtil.subPre(blog.getContentText().trim(), 50));
        }
    }

    @GetMapping("/detail/{id}")
    public Result queryBlogById(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        Blog blog = blogService.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        Long currentUserId = jwt == null ? null : Long.parseLong(jwt.getSubject());
        if (blog.getStatus() != 1 && (currentUserId == null || !currentUserId.equals(blog.getUserId()))) {
            return Result.fail("笔记不存在");
        }
        User author = userService.getById(blog.getUserId());
        blog.setName(author.getNickName());
        blog.setIcon(author.getIcon());
        if (currentUserId == null) {
            blog.setIsLike(false);
            blog.setFollowed(false);
        }
        if (StrUtil.isNotBlank(blog.getContentObjectKey())) {
            blog.setContentUrl(ossStorageService.publicUrl(blog.getContentObjectKey()));
        }
        return Result.ok(blog);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return blogService.delete(userId, id);
    }

    @GetMapping("/cleansite")
    public Result deletelist(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .eq("status", 2)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            User u = userService.getById(blog.getUserId());
            blog.setName(u.getNickName());
            blog.setIcon(u.getIcon());
        });
        return Result.ok(records);
    }
}
