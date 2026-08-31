package com.specialweek.blog.api;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.blog.api.dto.FeedPageResponse;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.BlogFeedService;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.util.SystemConstants;
import com.specialweek.common.web.Result;
import com.specialweek.common.web.ScrollResult;
import com.specialweek.counter.dto.BlogFlags;
import com.specialweek.counter.dto.CounterActionResult;
import com.specialweek.counter.service.BitmapStateReader;
import com.specialweek.counter.service.CounterService;
import com.specialweek.follow.service.FollowStateService;
import com.specialweek.limiter.annotation.RateLimiter;
import com.specialweek.storage.OssStorageService;
import com.specialweek.user.domain.User;
import com.specialweek.user.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private CounterService counterService;
    @Resource
    private BitmapStateReader bitmapStateReader;
    @Resource
    private FollowStateService followStateService;
    @Resource
    private BlogFeedService blogFeedService;

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
        blogFeedService.invalidateFeedRanking();
        return Result.ok(blog.getId());
    }

    @PutMapping("/{blogId}/like")
    public Result like(@PathVariable long blogId, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        requirePublished(blogId);
        boolean changed = counterService.like("blog", String.valueOf(blogId), userId);
        long count = counterService.getCounts("blog", String.valueOf(blogId), List.of("like"))
                .getOrDefault("like", 0L);
        return Result.ok(new CounterActionResult(blogId, true, changed, count));
    }

    @DeleteMapping("/{blogId}/like")
    public Result unlike(@PathVariable long blogId, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        requirePublished(blogId);
        boolean changed = counterService.unlike("blog", String.valueOf(blogId), userId);
        long count = counterService.getCounts("blog", String.valueOf(blogId), List.of("like"))
                .getOrDefault("like", 0L);
        return Result.ok(new CounterActionResult(blogId, false, changed, count));
    }

    @PutMapping("/{blogId}/favorite")
    public Result favorite(@PathVariable long blogId, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        requirePublished(blogId);
        boolean changed = counterService.fav("blog", String.valueOf(blogId), userId);
        long count = counterService.getCounts("blog", String.valueOf(blogId), List.of("fav"))
                .getOrDefault("fav", 0L);
        return Result.ok(new CounterActionResult(blogId, true, changed, count));
    }

    @DeleteMapping("/{blogId}/favorite")
    public Result unfavorite(@PathVariable long blogId, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        requirePublished(blogId);
        boolean changed = counterService.unfav("blog", String.valueOf(blogId), userId);
        long count = counterService.getCounts("blog", String.valueOf(blogId), List.of("fav"))
                .getOrDefault("fav", 0L);
        return Result.ok(new CounterActionResult(blogId, false, changed, count));
    }

    private void requirePublished(long blogId) {
        Blog blog = blogService.getById(blogId);
        if (blog == null || blog.getStatus() == null || blog.getStatus() != 1) {
            throw new IllegalArgumentException("博客不存在或未发布");
        }
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current,
                              @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        Page<Blog> page = blogService.query()
                .eq("user_id", userId)
                .orderByDesc("update_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        overlayCounts(records);
        overlayUserState(records, userId);
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current,
                               @AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = jwt == null ? null : Long.parseLong(jwt.getSubject());
        FeedPageResponse feed = blogFeedService.getPublicFeed(current, SystemConstants.MAX_PAGE_SIZE, currentUserId);
        return Result.ok(feed.items());
    }

    @GetMapping("/of/user")
    public Result queryBlogOfUser(@RequestParam("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current,
                                  @AuthenticationPrincipal Jwt jwt) {
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .eq("status", 1)
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        overlayCounts(records);
        if (jwt != null) {
            overlayUserState(records, Long.parseLong(jwt.getSubject()));
        } else {
            records.forEach(this::resetUserState);
        }
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long lastId,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                    @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        ScrollResult scroll = blogService.queryFeedOfFollow(userId, lastId, offset);
        List<Blog> records = toBlogList(scroll.getList());
        overlayAuthorInfo(records);
        overlayCounts(records);
        overlayUserState(records, userId);
        return Result.ok(scroll);
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

        if (blog.getStatus() != null && blog.getStatus() == 1) {
            Map<String, Long> counts = counterService.getCounts("blog", String.valueOf(blog.getId()),
                    List.of("like", "fav"));
            blog.setLiked(clampInt(counts.getOrDefault("like", 0L)));
            blog.setFavorites(clampInt(counts.getOrDefault("fav", 0L)));
        }

        if (currentUserId == null) {
            resetUserState(blog);
        } else {
            BlogFlags flags = bitmapStateReader.getFlagsBatch(List.of(blog.getId()), currentUserId)
                    .get(blog.getId());
            blog.setIsLike(flags != null && flags.liked());
            blog.setFaved(flags != null && flags.favorited());
            blog.setFollowed(followStateService.isFollowed(currentUserId, blog.getUserId()));
        }
        if (StrUtil.isNotBlank(blog.getContentObjectKey())) {
            blog.setContentUrl(ossStorageService.publicUrl(blog.getContentObjectKey()));
        }
        return Result.ok(blog);
    }

    @PostMapping("/draft")
    @RateLimiter(
            key = "save:draft:",
            window = 60,
            limit = 3,
            message = "操作过于频繁，请稍后再试",
            type = RateLimiter.LimitType.USER
    )
    public Result saveDraft(@RequestBody Blog blog, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        Blog old = null;
        if (blog.getId() != null) {
            old = blogService.getById(blog.getId());
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
            if (old != null && old.getStatus() != null && old.getStatus() == 1) {
                blogFeedService.invalidateFeedCache(blog.getId());
                blogFeedService.invalidateFeedRanking();
            }
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
        blogFeedService.invalidateFeedRanking();
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        Result result = blogService.delete(userId, id);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            blogFeedService.invalidateFeedCache(id);
            blogFeedService.invalidateFeedRanking();
        }
        return result;
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

    private void overlayCounts(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }
        List<Long> ids = blogs.stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                .map(Blog::getId)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<String, Map<String, Long>> raw = counterService.getCountsBatch("blog",
                ids.stream().map(String::valueOf).toList(), List.of("like", "fav"));
        blogs.forEach(b -> {
            Map<String, Long> m = raw.get(String.valueOf(b.getId()));
            if (m != null) {
                b.setLiked(clampInt(m.getOrDefault("like", 0L)));
                b.setFavorites(clampInt(m.getOrDefault("fav", 0L)));
            }
        });
    }

    private void overlayUserState(List<Blog> blogs, long userId) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }
        List<Long> ids = blogs.stream().map(Blog::getId).toList();
        Map<Long, BlogFlags> flags = bitmapStateReader.getFlagsBatch(ids, userId);
        List<Long> authorIds = blogs.stream().map(Blog::getUserId).distinct().toList();
        Map<Long, Boolean> followStates = followStateService.getBatch(userId, authorIds);
        blogs.forEach(b -> {
            BlogFlags f = flags.get(b.getId());
            b.setIsLike(f != null && f.liked());
            b.setFaved(f != null && f.favorited());
            Boolean followed = followStates.get(b.getUserId());
            b.setFollowed(followed != null && followed);
        });
    }

    private void overlayAuthorInfo(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }
        List<Long> authorIds = blogs.stream().map(Blog::getUserId).distinct().toList();
        Map<Long, User> users = userService.listByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        blogs.forEach(b -> {
            User u = users.get(b.getUserId());
            if (u != null) {
                b.setName(u.getNickName());
                b.setIcon(u.getIcon());
            }
        });
    }

    private void resetUserState(Blog blog) {
        blog.setIsLike(false);
        blog.setFaved(false);
        blog.setFollowed(false);
    }

    @SuppressWarnings("unchecked")
    private static List<Blog> toBlogList(List<?> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<Blog> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Blog b) {
                out.add(b);
            }
        }
        return out;
    }

    private static int clampInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private void fillDescription(Blog blog) {
        if (StrUtil.isBlank(blog.getDescription()) && StrUtil.isNotBlank(blog.getContentText())) {
            blog.setDescription(StrUtil.subPre(blog.getContentText().trim(), 50));
        }
    }
}
