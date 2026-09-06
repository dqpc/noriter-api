package games.noriter.api.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class VisitModuleTests {

    @Autowired VisitService visits;

    @Test
    void countsEachVisitorOncePerDay() {
        var before = visits.stats();
        var first = "visitor-" + UUID.randomUUID();
        var a = visits.record(first);
        var again = visits.record(first);
        var b = visits.record("visitor-" + UUID.randomUUID());
        assertThat(a.today()).isEqualTo(before.today() + 1);
        assertThat(again).isEqualTo(a);
        assertThat(b.today()).isEqualTo(before.today() + 2);
        assertThat(b.total()).isEqualTo(before.total() + 2);
        assertThat(visits.stats()).isEqualTo(b);
    }
}
