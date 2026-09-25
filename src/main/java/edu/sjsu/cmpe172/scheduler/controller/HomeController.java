package edu.sjsu.cmpe172.scheduler.controller;

import edu.sjsu.cmpe172.scheduler.dto.HomeResponse;
import edu.sjsu.cmpe172.scheduler.dto.ProviderDto;
import edu.sjsu.cmpe172.scheduler.dto.ServiceDto;
import edu.sjsu.cmpe172.scheduler.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HomeController {

    private final CatalogService catalog;

    public HomeController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/home")
    public HomeResponse home() {
        return catalog.home();
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return catalog.listServices();
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return catalog.listProviders();
    }
}
