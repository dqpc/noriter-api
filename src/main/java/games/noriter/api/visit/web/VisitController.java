package games.noriter.api.visit.web;

import games.noriter.api.visit.VisitService;
import games.noriter.api.visit.web.dto.VisitStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
class VisitController {

    private final VisitService visits;

    @PostMapping
    VisitStatsResponse record() {
        return VisitStatsResponse.from(visits.record());
    }

    @GetMapping
    VisitStatsResponse stats() {
        return VisitStatsResponse.from(visits.stats());
    }
}
