package edu.sjsu.cmpe172.scheduler.service;

import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a minute, stores COMPLETED on BOOKED appointments whose slot has ended. */
@Component
public class AppointmentStatusJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusJob.class);

    private final AppointmentRepository appointments;

    public AppointmentStatusJob(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT10S")
    public void markPastAppointmentsCompleted() {
        int changed = appointments.markPastAsCompleted();
        if (changed > 0) {
            log.info("Marked {} past appointment(s) COMPLETED", changed);
        }
    }
}
