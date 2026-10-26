package edu.sjsu.cmpe172.scheduler.notification;

import edu.sjsu.cmpe172.scheduler.model.AppointmentView;
import edu.sjsu.cmpe172.scheduler.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Commit first, then notify. Runs only AFTER_COMMIT of the booking/cancel transaction, and
 * on a background thread (@Async), so the HTTP response to the customer never waits for
 * the notification service, however slow or broken it is.
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US);

    private final AppointmentRepository appointments;
    private final NotificationSender sender;
    private final NotificationProperties props;

    public AppointmentNotificationListener(AppointmentRepository appointments, NotificationSender sender,
                                           NotificationProperties props) {
        this.appointments = appointments;
        this.sender = sender;
        this.props = props;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentChangedEvent event) {
        if (!props.enabled()) {
            return;
        }
        try {
            AppointmentView appt = appointments.findViewById(event.appointmentId()).orElse(null);
            String email = appointments.findCustomerEmail(event.appointmentId()).orElse(null);
            if (appt == null || email == null) {
                log.warn("Appointment {} vanished before its {} notification was sent",
                        event.appointmentId(), event.type());
                return;
            }
            sender.deliver(new NotificationRequest(
                    NotificationRequest.keyFor(appt.id(), event.type()),
                    email,
                    event.type(),
                    message(appt, event.type()),
                    appt.id()));
        } catch (RuntimeException e) {
            // Even the fallback failed (e.g. the database is down too). The booking itself is safe.
            log.error("Could not send or park {} notification for appointment {}",
                    event.type(), event.appointmentId(), e);
        }
    }

    static String message(AppointmentView a, NotificationType type) {
        String what = a.serviceName() + " with " + a.providerName() + " on " + WHEN.format(a.startTime());
        return switch (type) {
            case BOOKING_CONFIRMED -> "Hi " + a.customerName() + ", your " + what
                    + " is confirmed (appointment #" + a.id() + ").";
            case BOOKING_CANCELLED -> "Hi " + a.customerName() + ", your " + what
                    + " has been cancelled (appointment #" + a.id() + ").";
        };
    }
}
