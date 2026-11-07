package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseSearchPerformanceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;

    @Test
    @DisplayName("NFR1-AC1: searching 1,000 matching courses returns them all in under 3000 ms")
    void searchIsFast() throws Exception {
        Semester spring = semesterRepository.findByCode("114-2").orElseThrow();
        List<Course> extra = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            extra.add(new Course(String.format("PF%04d", i), "Perf Course " + i, 3, "Prof. Perf", 50, 50, spring));
        }
        courseRepository.saveAll(extra);
        try {
            long start = System.nanoTime();
            var result = mockMvc.perform(get("/api/courses").param("semester", "114-2").param("q", "perf")
                    .with(user(ALICE_ID).roles("STUDENT")));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.courses.length()").value(1000));
            assertThat(elapsedMs).as("elapsed ms").isLessThan(3000);
        } finally {
            courseRepository.deleteAll(courseRepository.findAll().stream()
                    .filter(c -> c.getCourseCode().startsWith("PF")).toList());
        }
    }
}
