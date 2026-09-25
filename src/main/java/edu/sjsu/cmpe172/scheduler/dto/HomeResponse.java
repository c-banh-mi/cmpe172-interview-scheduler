package edu.sjsu.cmpe172.scheduler.dto;

import java.util.List;

public record HomeResponse(String appName, long openSlotCount, List<ServiceDto> services, List<ProviderDto> providers) {
}
