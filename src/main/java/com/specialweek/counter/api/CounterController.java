package com.specialweek.counter.api;

import com.specialweek.counter.api.dto.CountsResponse;
import com.specialweek.counter.schema.CounterSchema;
import com.specialweek.counter.service.CounterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/counter")
public class CounterController {

    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    @GetMapping("/{etype}/{eid}")
    public CountsResponse getCounts(@PathVariable("etype") String entityType,
                                    @PathVariable("eid") String entityId,
                                    @RequestParam(value = "metrics", required = false) String metricsStr) {
        List<String> metrics;
        if (metricsStr == null || metricsStr.isBlank()) {
            metrics = new ArrayList<>(CounterSchema.SUPPORTED_METRICS);
        } else {
            metrics = Arrays.stream(metricsStr.split(","))
                    .map(String::trim)
                    .filter(CounterSchema.SUPPORTED_METRICS::contains)
                    .toList();
        }

        Map<String, Long> counts = counterService.getCounts(entityType, entityId, metrics);
        return new CountsResponse(entityType, entityId, counts);
    }
}
