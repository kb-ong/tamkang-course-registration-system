package edu.tku.registration.service;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** TIDY-3: retry exhaustion logs exactly one WARN on the RegistrationService logger, without student data. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class RetryExhaustionLogTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private CourseRepository courseRepository;

    private Long courseId;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        Course c = new Course("TIDY3", "Tidy Log Test", 3, "Prof. Test", 50, 50,
                semesterRepository.findByCode("115-1").orElseThrow());
        courseId = courseRepository.saveAndFlush(c).getId();
        clearInvocations(courseRepository);
        logger = (Logger) LoggerFactory.getLogger(RegistrationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        jdbc.update("delete from audit_log where course_id = ?", courseId);
        jdbc.update("delete from registration where course_id = ?", courseId);
        jdbc.update("delete from course where id = ?", courseId);
    }

    private List<ILoggingEvent> warns() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Test
    @DisplayName("TIDY-3: register retry exhaustion logs exactly one WARN with course id and exception class, no student id")
    void registerExhaustionLogsOneWarn() {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(ALICE_ID, courseId))
                .isInstanceOf(RegistrationConflictException.class);

        List<ILoggingEvent> warns = warns();
        assertThat(warns).hasSize(1);
        String msg = warns.get(0).getFormattedMessage();
        assertThat(msg).contains(String.valueOf(courseId))
                .contains("ObjectOptimisticLockingFailureException")
                .contains("register")
                .doesNotContain(ALICE_ID);
    }

    @Test
    @DisplayName("TIDY-3: drop retry exhaustion logs exactly one WARN with course id and exception class, no student id")
    void dropExhaustionLogsOneWarn() {
        registrationService.register(ALICE_ID, courseId);
        appender.list.clear();
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.drop(ALICE_ID, courseId))
                .isInstanceOf(RegistrationConflictException.class);

        List<ILoggingEvent> warns = warns();
        assertThat(warns).hasSize(1);
        String msg = warns.get(0).getFormattedMessage();
        assertThat(msg).contains(String.valueOf(courseId))
                .contains("ObjectOptimisticLockingFailureException")
                .contains("drop")
                .doesNotContain(ALICE_ID);
    }
}
