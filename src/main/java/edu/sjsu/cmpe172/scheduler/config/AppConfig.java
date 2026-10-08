package edu.sjsu.cmpe172.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Services read "now" from this clock so unit tests can pin the time. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
