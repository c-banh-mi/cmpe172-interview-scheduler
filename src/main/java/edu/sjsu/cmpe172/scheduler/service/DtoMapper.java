package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.AppointmentDto;
import edu.sjsu.cmpe172.scheduler.dto.ProviderDto;
import edu.sjsu.cmpe172.scheduler.dto.ProviderSlotDto;
import edu.sjsu.cmpe172.scheduler.dto.ServiceDto;
import edu.sjsu.cmpe172.scheduler.dto.SlotDto;
import edu.sjsu.cmpe172.scheduler.dto.UserDto;
import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.AppointmentView;
import edu.sjsu.cmpe172.scheduler.model.Provider;
import edu.sjsu.cmpe172.scheduler.model.ServiceOffering;
import edu.sjsu.cmpe172.scheduler.model.SlotView;

import java.math.BigDecimal;

/** Converts internal row types into API DTOs so controllers never expose database rows. */
public final class DtoMapper {

    private DtoMapper() {
    }

    static ProviderDto toDto(Provider p) {
        return new ProviderDto(p.id(), p.displayName(), p.headline());
    }

    static ServiceDto toDto(ServiceOffering s) {
        return new ServiceDto(s.id(), s.name(), s.description(), s.durationMinutes(), dollars(s.priceCents()));
    }

    static SlotDto toDto(SlotView s) {
        return new SlotDto(s.id(), s.providerId(), s.providerName(), s.serviceId(), s.serviceName(),
                s.durationMinutes(), dollars(s.priceCents()), s.startTime(), s.endTime());
    }

    static ProviderSlotDto toProviderSlotDto(SlotView s) {
        return new ProviderSlotDto(s.id(), s.serviceId(), s.serviceName(), s.startTime(), s.endTime(), s.status());
    }

    static AppointmentDto toDto(AppointmentView a) {
        return new AppointmentDto(a.id(), a.slotId(), a.serviceName(), a.providerName(), a.customerName(),
                a.startTime(), a.endTime(), a.status(), a.notes(), a.createdAt(), a.cancelledAt());
    }

    public static UserDto toDto(AppUser u) {
        return new UserDto(u.id(), u.username(), u.fullName(), u.role());
    }

    private static BigDecimal dollars(int cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
