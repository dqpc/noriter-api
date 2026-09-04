package games.noriter.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** 모듈 경계 검증. 다른 모듈의 내부(package-private 이 아닌 non-API 타입)를 참조하거나 순환이 생기면 실패한다. */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(NoriterApiApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    @Test
    void writesDocumentation() {
        // build/spring-modulith-docs 에 모듈 다이어그램(PlantUML) 과 캔버스를 생성한다.
        new Documenter(modules).writeDocumentation();
    }
}
