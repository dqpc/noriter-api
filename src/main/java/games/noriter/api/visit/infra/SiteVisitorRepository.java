package games.noriter.api.visit.infra;

import games.noriter.api.visit.domain.SiteVisitor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteVisitorRepository extends JpaRepository<SiteVisitor, SiteVisitor.Key> {}
