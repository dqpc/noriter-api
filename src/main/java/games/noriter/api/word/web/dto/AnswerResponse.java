package games.noriter.api.word.web.dto;

import games.noriter.api.word.WordAnswer;

public record AnswerResponse(String jamo, String word, String meaning) {
    public static AnswerResponse from(WordAnswer a) {
        return new AnswerResponse(a.jamo(), a.word(), a.meaning());
    }
}
