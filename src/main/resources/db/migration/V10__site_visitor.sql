CREATE TABLE site_visitor (
    visit_day    DATE        NOT NULL,
    visitor_hash VARCHAR(64) NOT NULL,
    CONSTRAINT pk_site_visitor PRIMARY KEY (visit_day, visitor_hash)
);
