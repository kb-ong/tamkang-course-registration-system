package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.SemesterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CourseServiceTest {

    @Autowired
    private CourseService courseService;
    @Autowired
    private SemesterRepository semesterRepository;

    @Test
    @DisplayName("FR2-EX5: with no current semester the default listing is (null, empty); an explicit semester still works")
    void noCurrentSemester() {
        Semester s = semesterRepository.findByCode("115-1").orElseThrow();
        s.setCurrent(false);
        semesterRepository.saveAndFlush(s);

        CourseListing none = courseService.searchCourses("410000001", null, null);
        assertThat(none.semester()).isNull();
        assertThat(none.courses()).isEmpty();

        CourseListing explicit = courseService.searchCourses("410000001", "115-1", null);
        assertThat(explicit.courses()).extracting(CourseSummary::code)
                .containsExactly("CS101", "CS201", "MA101");
    }
}
