package edu.tku.registration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** @Import this to pin the application Clock (default T_OPEN, inside semester 115-1). Autowire MutableTestClock to move it. */
@TestConfiguration
public class PinnedClockConfig {

    @Bean
    @Primary
    MutableTestClock pinnedTestClock() {
        return new MutableTestClock();
    }
}
