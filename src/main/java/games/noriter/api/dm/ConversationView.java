package games.noriter.api.dm;

import java.time.Instant;

/** 내 관점의 대화 하나. 1:1 이라 상대가 한 명 */
public record ConversationView(Long id, Long otherUserId, String otherNickname, String otherCharacterId,
                               MessageView lastMessage, long unread, Instant lastMessageAt) {}
