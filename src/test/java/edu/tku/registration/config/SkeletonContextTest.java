package edu.tku.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.RegistrationApplication;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.ClassUtils;
import javax.sql.DataSource;

@SpringBootTest
@AutoConfigureMockMvc
class SkeletonContextTest {

    @Autowired private ApplicationContext context;
    @Autowired private Clock clock;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private RegistrationPeriodRepository registrationPeriodRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("SK-AC1: context loads with exactly one Clock bean and a BCryptPasswordEncoder")
    void contextHasClockAndBcrypt() {
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(context.getBean(PasswordEncoder.class)).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("SK-AC2: production Clock bean uses the Asia/Taipei zone")
    void clockZoneIsTaipei() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Taipei"));
    }

    @Test
    @DisplayName("SK-AC3: seed row counts are 3 students, 2 semesters, 4 courses, 1 registration, 2 periods, 0 audit logs")
    void seedRowCounts() {
        assertThat(studentRepository.count()).as("students").isEqualTo(3);
        assertThat(semesterRepository.count()).as("semesters").isEqualTo(2);
        assertThat(courseRepository.count()).as("courses").isEqualTo(4);
        assertThat(registrationRepository.count()).as("registrations").isEqualTo(1);
        assertThat(registrationPeriodRepository.count()).as("registration periods").isEqualTo(2);
        assertThat(auditLogRepository.count()).as("audit logs").isEqualTo(0);
    }

    @Test
    @DisplayName("SK-AC7: the DataSource URL is a per-context in-memory H2 database jdbc:h2:mem:tku-<uuid>")
    void dataSourceUrlIsUniquePerContext() throws SQLException {
        assertThat(urlOf(dataSource)).containsPattern(
                "^jdbc:h2:mem:tku-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("SK-AC8: a second application context gets a different database and closing it leaves this one intact")
    void secondContextUsesSeparateDatabase() throws SQLException {
        String firstUrl = urlOf(dataSource);
        String secondUrl;
        try (ConfigurableApplicationContext second = new SpringApplicationBuilder(RegistrationApplication.class)
                .properties("server.port=0")
                .run()) {
            secondUrl = urlOf(second.getBean(DataSource.class));
        }

        assertThat(secondUrl).isNotEqualTo(firstUrl);
        assertThat(studentRepository.count()).as("students in the first context after the second closed").isEqualTo(3);
    }

    @Test
    @DisplayName("SK-AC9: the Thymeleaf and Hibernate Validator libraries are not on the classpath")
    void thymeleafAndValidationRemoved() {
        assertThat(ClassUtils.isPresent("org.thymeleaf.spring6.SpringTemplateEngine", null))
                .as("Thymeleaf SpringTemplateEngine present").isFalse();
        assertThat(ClassUtils.isPresent("org.hibernate.validator.HibernateValidator", null))
                .as("HibernateValidator present").isFalse();
    }

    private static String urlOf(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return c.getMetaData().getURL();
        }
    }
}
