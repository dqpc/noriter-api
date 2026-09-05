package games.noriter.api.visit.web.dto;

import games.noriter.api.visit.VisitStats;

public record VisitStatsResponse(long today, long total) {

    public static VisitStatsResponse from(VisitStats s) {
        return new VisitStatsResponse(s.today(), s.total());
    }
}
