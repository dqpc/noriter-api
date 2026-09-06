package games.noriter.api.word.web.dto;

import java.util.List;

/** 자리별 correct / present / absent */
public record GuessResponse(List<String> statuses) {}
