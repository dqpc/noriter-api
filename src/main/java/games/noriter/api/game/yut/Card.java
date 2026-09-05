package games.noriter.api.game.yut;

import java.util.LinkedHashMap;
import java.util.Map;

/** 잡기·방 도착·시작 때 뽑는 카드. 천사 4장 + 악마 1장 더미에서 한 장. */
enum Card {
    ONE_MORE(Kind.ANGEL, "한 번 더!", "던지기 한 번 추가"),
    CHOOSE_THROW(Kind.ANGEL, "골라 던져!", "다음 던지기 결과를 직접 고름 (윷·모라도 추가 던지기 없음)"),
    ONE_STEP(Kind.ANGEL, "한 칸 더!", "이 말이 그 자리에서 한 칸 전진"),
    NEW_PIECE(Kind.ANGEL, "새 말 나가!", "대기 말 하나가 도 자리에 바로 올라감"),
    STACK_UP(Kind.ANGEL, "업혀!", "같은 길 뒤에 있는 내 말이 이 자리로 와서 업힘"),
    SHIELD(Kind.ANGEL, "지켜줄게!", "다음 내 차례까지 내 말은 잡히지 않음"),
    BACKDO_IMMUNE(Kind.ANGEL, "빽도 무효!", "다음 내 차례까지 빽도는 도로 취급"),
    SHORTCUT(Kind.ANGEL, "지름길 열려!", "다음에 갈림길을 지나갈 때 정확히 서지 않아도 꺾을 수 있음"),
    GREED(Kind.ANGEL, "욕심 부려!", "다음 던지기 칸수 +1 (빽도 제외)"),

    FORFEIT(Kind.DEVIL, "몰수!", "잡기로 받은 추가 던지기가 사라짐"),
    RELEASE(Kind.DEVIL, "놓아줘!", "방금 잡은 상대 말이 제자리로 돌아와 같이 서 있음. 추가 던지기도 없음"),
    STEP_BACK(Kind.DEVIL, "뒷걸음!", "이 말이 한 칸 후퇴"),
    CURSED_BACKDO(Kind.DEVIL, "저주의 빽도!", "다음 던지기는 무조건 빽도"),
    REST(Kind.DEVIL, "쉬어!", "다음 내 차례를 한 번 건너뜀"),
    TARGET(Kind.DEVIL, "표적!", "다음 내 차례까지 내 말을 잡은 상대는 추가 던지기 2회"),
    SCATTER(Kind.DEVIL, "흩어져!", "업힌 무리의 맨 위 말 하나가 한 칸 뒤로 떨어짐");

    enum Kind { ANGEL, DEVIL }

    final Kind kind;
    final String label;
    final String description;

    Card(Kind kind, String label, String description) {
        this.kind = kind;
        this.label = label;
        this.description = description;
    }

    Map<String, Object> view() {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", name());
        m.put("kind", kind.name());
        m.put("label", label);
        m.put("description", description);
        return m;
    }
}
