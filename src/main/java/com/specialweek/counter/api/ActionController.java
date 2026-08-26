package com.specialweek.counter.api;

import com.specialweek.counter.api.dto.ActionRequest;
import com.specialweek.counter.service.CounterService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/action")
public class ActionController {

    private final CounterService counterService;

    public ActionController(CounterService counterService) {
        this.counterService = counterService;
    }

    @PostMapping("/like")
    public Map<String, Object> like(@Valid @RequestBody ActionRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.like(req.getEntityType(), req.getEntityId(), uid);
        return Map.of(
                "changed", changed,
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid));
    }

    @PostMapping("/unlike")
    public Map<String, Object> unlike(@Valid @RequestBody ActionRequest req,
                                      @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.unlike(req.getEntityType(), req.getEntityId(), uid);
        return Map.of(
                "changed", changed,
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid));
    }

    @PostMapping("/fav")
    public Map<String, Object> fav(@Valid @RequestBody ActionRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.fav(req.getEntityType(), req.getEntityId(), uid);
        return Map.of(
                "changed", changed,
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid));
    }

    @PostMapping("/unfav")
    public Map<String, Object> unfav(@Valid @RequestBody ActionRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.unfav(req.getEntityType(), req.getEntityId(), uid);
        return Map.of(
                "changed", changed,
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid));
    }
}
