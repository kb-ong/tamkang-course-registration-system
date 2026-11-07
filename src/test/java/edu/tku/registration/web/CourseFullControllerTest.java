package edu.tku.registration.web;

import static edu.tku.registration.service.CapacityTestData.*;
import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.MSG_STUDENT_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * FR6 HTTP criteria for POST /api/registrations. Not @Transactional: ZERO1 and LAST1 are created
 * and removed by each test. csrf() is fine here (no real CSRF cookie is needed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class CourseFullControllerTest {

    private static final RequestPostProcessor ALICE = user("410000001").roles("STUDENT");
    private static final RequestPostProcessor BOB = user("410000002").roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");
    private static final String CONFLICT = "The course was just updated by another student. Please try again.";

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long ma101;
    private Long zero1;

    @BeforeEach
    void setUp() {
        ma101 = courseRepository.findByCourseCodeAndSemester_Code("MA101", "115-1").orElseThrow().getId();
        zero1 = createCourse(courseRepository, semesterRepository, "ZERO1", 5, 0);
    }

    @AfterEach
    void tearDown() {
        removeCourse(jdbc, zero1);
    }

    private ResultActions register(RequestPostProcessor who, Long courseId) throws Exception {
        return mockMvc.perform(post("/api/registrations").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"));
    }

    @Test
    @DisplayName("FR6-AC1: POST for a 0-seat course returns 409 and leaves the course unchanged (MA101 as Bob, ZERO1 as Alice)")
    void blockedAtZero() throws Exception {
        long v = version(jdbc, ma101);
        long vz = version(jdbc, zero1);
        register(BOB, ma101).andExpect(status().isConflict());
        register(ALICE, zero1).andExpect(status().isConflict());
        assertThat(seats(jdbc, ma101)).isZero();
        assertThat(version(jdbc, ma101)).isEqualTo(v);
        assertThat(seats(jdbc, zero1)).isZero();
        assertThat(version(jdbc, zero1)).isEqualTo(vz);
    }

    @Test
    @DisplayName("FR6-AC2a: the 409 body is exactly {\"message\":\"Course Full: {code} has no available seats.\"}")
    void messageExact() throws Exception {
        register(BOB, ma101).andExpect(jsonError(409, "Course Full: MA101 has no available seats."));
        register(ALICE, zero1).andExpect(jsonError(409, "Course Full: ZERO1 has no available seats."));
    }

    @Test
    @DisplayName("FR6-AC2b: the message contains 'Course Full' and is never the generic conflict text")
    void containsCourseFull() throws Exception {
        for (String body : new String[] {
                register(BOB, ma101).andReturn().getResponse().getContentAsString(),
                register(ALICE, zero1).andReturn().getResponse().getContentAsString()}) {
            assertThat(body).contains("Course Full").doesNotContain(CONFLICT);
        }
    }

    @Test
    @DisplayName("FR6-AC3a: rejected POSTs add no registration or audit row and change no seats or version")
    void noRecord() throws Exception {
        int regs = totalRegs(jdbc);
        int audits = totalAudits(jdbc);
        long v = version(jdbc, ma101);
        long vz = version(jdbc, zero1);
        register(BOB, ma101).andExpect(status().isConflict());
        register(ALICE, zero1).andExpect(status().isConflict());
        assertThat(totalRegs(jdbc)).isEqualTo(regs);
        assertThat(totalAudits(jdbc)).isEqualTo(audits);
        assertThat(version(jdbc, ma101)).isEqualTo(v);
        assertThat(version(jdbc, zero1)).isEqualTo(vz);
        assertThat(seats(jdbc, zero1)).isZero();
    }

    @Test
    @DisplayName("FR6-AC3b: Alice takes the last seat (201), Bob then gets 409 Course Full; exactly one registration and one audit row, Alice's")
    void lastSeatThenFull() throws Exception {
        Long last1 = createCourse(courseRepository, semesterRepository, "LAST1", 1, 1);
        try {
            register(ALICE, last1).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.availableSeats").value(0));
            register(BOB, last1).andExpect(jsonError(409, "Course Full: LAST1 has no available seats."));

            assertThat(seats(jdbc, last1)).isZero();
            assertThat(regs(jdbc, last1)).isEqualTo(1);
            assertThat(audits(jdbc, last1)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select student_id from audit_log where course_id = ? and action = 'REGISTER'",
                    String.class, last1)).isEqualTo("410000001");
            // FR6-EX5
            assertThat(invariantGap(jdbc, last1)).isZero();
        } finally {
            removeCourse(jdbc, last1);
        }
    }

    @Test
    @DisplayName("FR6-EX6: Carol on full MA101 gets 'already registered'; unknown course 404; unknown student 404")
    void checkOrder() throws Exception {
        register(CAROL, ma101).andExpect(jsonError(409, "You are already registered for MA101."));
        register(ALICE, 999999L).andExpect(jsonError(404, "Course not found."));
        register(user("499999999").roles("STUDENT"), ma101).andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
    }

    @Test
    @DisplayName("FR6-EX7: anonymous gets 401 and a missing CSRF token gets 403 on the full MA101; nothing is written")
    void securityBeforeFull() throws Exception {
        int regs = totalRegs(jdbc);
        int audits = totalAudits(jdbc);
        mockMvc.perform(post("/api/registrations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + ma101 + "}"))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED));
        mockMvc.perform(post("/api/registrations").with(BOB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + ma101 + "}"))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));
        assertThat(totalRegs(jdbc)).isEqualTo(regs);
        assertThat(totalAudits(jdbc)).isEqualTo(audits);
        assertThat(seats(jdbc, ma101)).isZero();
    }

    @Test
    @DisplayName("FR6-EX8: a course with seats left still returns 201 with the decremented seat count")
    void seatsLeftUnaffected() throws Exception {
        Long c = createCourse(courseRepository, semesterRepository, "LEFT3", 3, 3);
        try {
            register(ALICE, c).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.availableSeats").value(2));
        } finally {
            removeCourse(jdbc, c);
        }
    }
}
