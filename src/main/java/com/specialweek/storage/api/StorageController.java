package com.specialweek.storage.api;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.specialweek.blog.domain.Blog;
import com.specialweek.blog.service.IBlogService;
import com.specialweek.common.web.Result;
import com.specialweek.storage.OssStorageService;
import com.specialweek.storage.config.OssProperties;
import com.specialweek.storage.dto.PresignRequest;
import com.specialweek.storage.dto.PresignResponse;
import com.specialweek.user.api.dto.UserDTO;
import com.specialweek.user.util.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
                objectKey = "blogs/" + postId + "/content" + ext;
                break;
            case "blog_cover":
                objectKey = "blogs/" + postId + "/cover/" + UUID.randomUUID() + ext;
                break;
            case "blog_image":
            default:
                String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
                objectKey = "blogs/" + postId + "/images/" + date + "/" + UUID.randomUUID() + ext;
                break;
        }
        //发签名
        int expiresIn = 600;
        String putUrl = ossStorageService.generatePresignedPutUrl(objectKey, request.getContentType(), expiresIn);

        //返回：前端直传时必须带这个 Content-Type（和签名一致）
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        return Result.ok(new PresignResponse(objectKey, putUrl, headers, expiresIn));
    }

    /**
     * 删除对象（换图/删草稿的时候用）
     * @param objectKey
     * @return
     */
    @DeleteMapping("/object")
    public Result delete(@RequestParam("key") String objectKey) {
        ossStorageService.deleteObject(objectKey);
        return Result.ok();
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
