package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.dto.HomeResponse;
import edu.sjsu.cmpe172.scheduler.dto.ProviderDto;
import edu.sjsu.cmpe172.scheduler.dto.ServiceDto;
import edu.sjsu.cmpe172.scheduler.model.SlotFilter;
import edu.sjsu.cmpe172.scheduler.repository.ProviderRepository;
import edu.sjsu.cmpe172.scheduler.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.scheduler.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only catalog: providers, services, and the home-page summary. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final SlotRepository slots;

    public CatalogService(ProviderRepository providers, ServiceOfferingRepository services, SlotRepository slots) {
        this.providers = providers;
        this.services = services;
        this.slots = slots;
    }

    public List<ProviderDto> listProviders() {
        return providers.findAll().stream().map(DtoMapper::toDto).toList();
    }

    public List<ServiceDto> listServices() {
        return services.findAll().stream().map(DtoMapper::toDto).toList();
    }

    public HomeResponse home() {
        long openSlots = slots.countOpen(new SlotFilter(null, null, null));
        return new HomeResponse("Interview Scheduler", openSlots, listServices(), listProviders());
    }
}
