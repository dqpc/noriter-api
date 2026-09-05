package games.noriter.api.visit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class VisitModuleTests {

    @Autowired VisitService visits;

    @Test
    void recordsPerDayAndTotal() {
        var before = visits.stats();
        var a = visits.record();
        var b = visits.record();
        assertThat(a.today()).isEqualTo(before.today() + 1);
        assertThat(b.today()).isEqualTo(before.today() + 2);
        assertThat(b.total()).isEqualTo(before.total() + 2);
        assertThat(visits.stats()).isEqualTo(b);
    }
}
