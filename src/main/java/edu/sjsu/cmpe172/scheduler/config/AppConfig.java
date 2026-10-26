package edu.sjsu.cmpe172.scheduler.config;

import edu.sjsu.cmpe172.scheduler.notification.NotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableAsync   // notifications are sent on a background thread
@EnableConfigurationProperties(NotificationProperties.class)
public class AppConfig {

    /** Services read "now" from this clock so unit tests can pin the time. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
