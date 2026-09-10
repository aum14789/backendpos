-- V42: Printer Management (Backoffice-configurable thermal printers per branch)
-- ADR 0005: printers are branch-owned master data synced to POS via /sync/pull.
-- Every printer is an 80mm thermal receipt printer on the branch LAN.

CREATE TABLE IF NOT EXISTS printers (
    id              VARCHAR(36) PRIMARY KEY,
    branch_id       VARCHAR(36) NOT NULL REFERENCES branches(id),
    name            VARCHAR(120) NOT NULL,
    ip_address      VARCHAR(45)  NOT NULL,          -- IPv4/IPv6/hostname
    port            INTEGER      NOT NULL DEFAULT 9100,
    is_document_printer BOOLEAN  NOT NULL DEFAULT FALSE, -- พิมพ์ใบเสร็จ/ใบแจ้งรายการ/ใบกำกับภาษี
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_printers_branch ON printers(branch_id);

-- หมวดอาหารที่เครื่องนี้รับพิมพ์ (1 เครื่องพิมพ์ได้หลายหมวด, 1 หมวดส่งได้หลายเครื่อง)
CREATE TABLE IF NOT EXISTS printer_menu_categories (
    printer_id   VARCHAR(36) NOT NULL REFERENCES printers(id) ON DELETE CASCADE,
    category_id  VARCHAR(36) NOT NULL REFERENCES menu_categories(id),
    PRIMARY KEY (printer_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_printer_menu_categories_category ON printer_menu_categories(category_id);
