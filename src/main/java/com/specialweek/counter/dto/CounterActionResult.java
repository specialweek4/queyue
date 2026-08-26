package com.specialweek.counter.dto;

public record CounterActionResult(
        long blogId,
        boolean active,
        boolean changed,
        long count) {
}
