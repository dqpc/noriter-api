package games.noriter.api.visit.web;

import games.noriter.api.visit.VisitService;
import games.noriter.api.visit.web.dto.VisitStatsResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    VisitStatsResponse record(HttpServletRequest request) {
        return VisitStatsResponse.from(visits.record(clientIp(request) + "|" + request.getHeader("User-Agent")));
    }

    @GetMapping
    VisitStatsResponse stats() {
        return VisitStatsResponse.from(visits.stats());
    }

    /** Cloudflare 워커를 거쳐 오므로 원 IP 는 헤더에 있다 */
    private static String clientIp(HttpServletRequest request) {
        var cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
