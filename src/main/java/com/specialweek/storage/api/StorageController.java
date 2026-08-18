package com.specialweek.storage.api;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.storage.config.OssProperties;
import com.specialweek.storage.dto.ConfirmRequest;
import com.specialweek.storage.dto.ConfirmResponse;
import com.specialweek.storage.dto.ConfirmedObject;
import com.specialweek.storage.dto.PresignRequest;
import com.specialweek.storage.dto.PresignResponse;
import com.specialweek.user.api.dto.UserDTO;
import com.specialweek.user.util.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageController {

    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private IBlogService blogService;
    @Resource
    private OssProperties ossProperties;

    @PostMapping("/presign")
    public Result presign(@RequestBody PresignRequest request){
        //TODO 后期改成Security
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }
        Blog post = blogService.getById(postId);
        if (post == null || !user.getId().equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        String scene = request.getScene();
        String ext = normalizeExt(request.getExt(), request.getContentType(), scene);
        String objectKey;
        switch (scene) {
            case "blog_content":
                // 正文固定一个 key，重复上传即覆盖旧版本
                objectKey = "unconfirmed/" + postId + "/content" + ext;
                break;
            case "blog_cover":
                objectKey = "unconfirmed/" + postId + "/cover" + ext;
                break;
            case "blog_image":
            default:
                String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
                objectKey = "unconfirmed/" + postId + "/images/" + date + "/" + UUID.randomUUID() + ext;
                break;
        }
        //发签名
        int expiresIn = 600;
        String putUrl = ossStorageService.generatePresignedPutUrl(objectKey, request.getContentType(), expiresIn);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        return Result.ok(new PresignResponse(objectKey, putUrl, headers, expiresIn));
    }

    /**
     * 确认提交:把临时区 unconfirmed/{postId}/ 下的对象搬入正式区 blogs/{postId}/，
     * 返回正式区引用。已是 blogs/ 前缀的 key 视为已确认，原样返回（幂等）。
     * 数据库只保存本接口返回的正式区引用，生命周期规则只清理 unconfirmed/ 前缀，绝不误删被引用文件。
     */
    @PostMapping("/confirm")
    public Result confirm(@RequestBody ConfirmRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        long postId;
        try {
            postId = Long.parseLong(request.getPostId());
        } catch (NumberFormatException e) {
            return Result.fail("postId 非法");
        }
        Blog post = blogService.getById(postId);
        if (post == null || !user.getId().equals(post.getUserId())) {
            return Result.fail("草稿不存在或无权限");
        }

        ConfirmResponse resp = new ConfirmResponse();
        List<ConfirmedObject> images = new ArrayList<>();
        if (request.getImageKeys() != null) {
            for (String key : request.getImageKeys()) {
                if (StrUtil.isBlank(key)) {
                    continue;
                }
                ConfirmedObject co = confirmObject(postId, key, "images/");
                if (co == null) {
                    return Result.fail("imageKey 非法: " + key);
                }
                images.add(co);
            }
        }
        resp.setImages(images);

        if (StrUtil.isNotBlank(request.getCoverKey())) {
            ConfirmedObject co = confirmObject(postId, request.getCoverKey(), "cover");
            if (co == null) {
                return Result.fail("coverKey 非法");
            }
            resp.setCover(co);
        }

        if (StrUtil.isNotBlank(request.getContentKey())) {
            ConfirmedObject co = confirmObject(postId, request.getContentKey(), "content.");
            if (co == null) {
                return Result.fail("contentKey 非法");
            }
            resp.setContent(co);
        }
        return Result.ok(resp);
    }

    /**
     * 校验 key 归属并搬移：
     * - blogs/{postId}/{segment}... 已确认，直接返回（幂等）
     * - unconfirmed/{postId}/{segment}... 复制到 blogs/ 对应位置并清理临时对象
     * - 其他情况返回 null（非法）
     */
    private ConfirmedObject confirmObject(long postId, String key, String segment) {
        if (key.startsWith("blogs/" + postId + "/" + segment)) {
            return new ConfirmedObject(key, ossStorageService.publicUrl(key));
        }
        if (key.startsWith("unconfirmed/" + postId + "/" + segment)) {
            String target = "blogs/" + key.substring("unconfirmed/".length());
            ossStorageService.copyObject(key, target);
            // 临时对象删除失败不影响提交，交给生命周期规则兜底,这里交给了生命周期完成
//            try {
//                ossStorageService.deleteObject(key);
//            } catch (Exception ignored) {
//                // 孤儿临时对象由 unconfirmed/ 生命周期规则回收
//            }
            return new ConfirmedObject(target, ossStorageService.publicUrl(target));
        }
        return null;
    }

    /**
     * 后缀
     * @param ext
     * @param contentType
     * @param scene
     * @return
     */
    private String normalizeExt(String ext, String contentType, String scene) {
        if (StrUtil.isNotBlank(ext)) {
            return ext.startsWith(".") ? ext : "." + ext;
        }
        if ("blog_content".equals(scene)) {
            switch (contentType) {
                case "text/markdown": return ".md";
                case "text/html":     return ".html";
                case "text/plain":    return ".txt";
                default:              return ".bin";
            }
        }
        switch (contentType) {
            case "image/jpeg": return ".jpg";
            case "image/png":  return ".png";
            case "image/webp": return ".webp";
            default:           return ".img";
        }
    }
}
