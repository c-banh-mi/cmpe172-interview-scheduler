package edu.sjsu.cmpe172.scheduler.dto;

import java.math.BigDecimal;

public record ServiceDto(long id, String name, String description, int durationMinutes, BigDecimal price) {
}
