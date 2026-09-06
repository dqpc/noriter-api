package games.noriter.api.dm;

import games.noriter.api.dm.domain.Conversation;
import games.noriter.api.dm.domain.ConversationMember;
import games.noriter.api.dm.domain.Message;
import games.noriter.api.dm.infra.ConversationMemberRepository;
import games.noriter.api.dm.infra.ConversationRepository;
import games.noriter.api.dm.infra.MessageRepository;
import games.noriter.api.realtime.RealtimeService;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1:1 쪽지. 계정끼리만, 둘 중 한쪽이라도 상대를 친구로 둔 사이에서만. */
@Service
@RequiredArgsConstructor
public class DmService {

    static final int PAGE = 50;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final UserService users;
    private final RealtimeService realtime;
    private final Clock clock;

    /** 상대와의 대화를 찾거나 만든다 */
    @Transactional
    public ConversationView open(Long me, Long other) {
        if (me.equals(other)) throw new DmException(DmException.Kind.INVALID, "자기 자신에게는 보낼 수 없습니다");
        if (users.findById(other).isEmpty()) throw new DmException(DmException.Kind.NOT_FOUND, "없는 사용자입니다");
        if (!users.isFriend(me, other) && !users.isFriend(other, me)) {
            throw new DmException(DmException.Kind.FORBIDDEN, "친구로 추가한 사이에서만 쪽지를 보낼 수 있습니다");
        }
        var conv = conversations.findByDmKey(Conversation.dmKey(me, other)).orElseGet(() -> {
            var now = Instant.now(clock);
            var c = conversations.save(Conversation.dm(me, other, now));
            members.save(new ConversationMember(c.getId(), me, now));
            members.save(new ConversationMember(c.getId(), other, now));
            return c;
        });
        return view(conv, requireMember(conv.getId(), me));
    }

    @Transactional(readOnly = true)
    public List<ConversationView> list(Long me) {
        var out = new ArrayList<ConversationView>();
        for (var m : members.findByUserId(me)) {
            conversations.findById(m.getConversationId()).ifPresent(c -> out.add(view(c, m)));
        }
        out.sort(Comparator.comparing((ConversationView v) -> v.lastMessageAt() == null ? Instant.EPOCH : v.lastMessageAt()).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public long unreadTotal(Long me) {
        long total = 0;
        for (var m : members.findByUserId(me)) total += messages.countByConversationIdAndIdGreaterThan(m.getConversationId(), m.getLastReadMessageId());
        return total;
    }

    /** beforeId 보다 오래된 메시지를 최신순으로 PAGE 개. beforeId 가 null 이면 최신부터 */
    @Transactional(readOnly = true)
    public List<MessageView> messages(Long me, Long conversationId, Long beforeId) {
        requireMember(conversationId, me);
        return messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, PAGE))
                .stream().map(Message::toView).toList();
    }

    @Transactional
    public MessageView send(Long me, Long conversationId, String text) {
        var member = requireMember(conversationId, me);
        var body = text == null ? "" : text.strip();
        if (body.isEmpty()) throw new DmException(DmException.Kind.INVALID, "내용이 없습니다");
        if (body.length() > Message.MAX_LENGTH) throw new DmException(DmException.Kind.INVALID, "쪽지는 " + Message.MAX_LENGTH + "자까지입니다");
        var now = Instant.now(clock);
        var saved = messages.save(new Message(conversationId, me, body, now));
        conversations.findById(conversationId).ifPresent(c -> c.touched(now));
        member.readUpTo(saved.getId());
        var view = saved.toView();
        for (var m : members.findByConversationId(conversationId)) {
            long unread = messages.countByConversationIdAndIdGreaterThan(conversationId, m.getLastReadMessageId());
            realtime.send(m.getUserId(), new DmPushed(view, unread));
        }
        return view;
    }

    @Transactional
    public void markRead(Long me, Long conversationId, long lastReadMessageId) {
        requireMember(conversationId, me).readUpTo(lastReadMessageId);
    }

    private ConversationMember requireMember(Long conversationId, Long me) {
        return members.findByConversationIdAndUserId(conversationId, me)
                .orElseThrow(() -> new DmException(DmException.Kind.NOT_FOUND, "없는 대화입니다"));
    }

    private ConversationView view(Conversation c, ConversationMember mine) {
        var otherId = members.findByConversationId(c.getId()).stream().map(ConversationMember::getUserId)
                .filter(id -> !id.equals(mine.getUserId())).findFirst().orElse(null);
        Map<Long, UserSummary> names = otherId == null ? Map.of() : users.findSummaries(List.of(otherId));
        var other = names.get(otherId);
        var last = messages.findFirstByConversationIdOrderByIdDesc(c.getId()).map(Message::toView).orElse(null);
        long unread = messages.countByConversationIdAndIdGreaterThan(c.getId(), mine.getLastReadMessageId());
        var character = otherId == null ? null : users.characterOf(otherId);
        return new ConversationView(c.getId(), otherId, other == null ? "?" : other.nickname(), character, last, unread, c.getLastMessageAt());
    }
}
