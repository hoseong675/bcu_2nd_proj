-- =====================================================================
-- AI 기반 커스텀 PC 견적 추천 서비스 - DB 스키마 (MySQL 8.x / InnoDB)
-- 설계 문서: schema.md  ·  대상 DB: pc_quote (utf8mb4)
-- 적용:  mysql -upcquote -p pc_quote < schema.sql
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 회원
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id            BIGINT       NOT NULL AUTO_INCREMENT,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    nickname           VARCHAR(50)  NOT NULL,
    hw_knowledge_level ENUM('초급','중급','고급') NOT NULL DEFAULT '초급',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. 견적 요청/설문 (정량+정성 요구사항)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS survey_requests (
    request_id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    purpose            ENUM('게이밍','영상편집','사무','AI','기타') NOT NULL,
    budget_min         INT          NULL,
    budget_max         INT          NULL,
    resolution_target  VARCHAR(20)  NULL,          -- 1080p / 1440p / 4K
    detail_requirement TEXT         NULL,          -- 정성적 요구사항
    status             ENUM('요청','처리중','완료','실패') NOT NULL DEFAULT '요청',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id),
    KEY idx_survey_user (user_id),
    CONSTRAINT fk_survey_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. 부품 카테고리
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS part_categories (
    category_id INT         NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20) NOT NULL,   -- CPU/GPU/MB/RAM/PSU/COOLER/STORAGE/CASE
    name        VARCHAR(50) NOT NULL,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. 부품 마스터
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parts (
    part_id      BIGINT       NOT NULL AUTO_INCREMENT,
    category_id  INT          NOT NULL,
    manufacturer VARCHAR(100) NULL,
    model_name   VARCHAR(200) NOT NULL,
    release_date DATE         NULL,     -- 최근 2년 필터용
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (part_id),
    KEY idx_parts_cat_release (category_id, release_date),
    CONSTRAINT fk_parts_category FOREIGN KEY (category_id)
        REFERENCES part_categories (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. 가격 캐싱 (일 배치 업데이트)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS part_prices (
    price_id      BIGINT       NOT NULL AUTO_INCREMENT,
    part_id       BIGINT       NOT NULL,
    source        ENUM('다나와','네이버') NOT NULL,
    price         INT          NOT NULL,
    product_url   VARCHAR(500) NULL,
    is_lowest     BOOLEAN      NOT NULL DEFAULT FALSE,
    snapshot_date DATE         NOT NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (price_id),
    UNIQUE KEY uk_price_part_src_date (part_id, source, snapshot_date),
    KEY idx_price_part_date (part_id, snapshot_date),
    CONSTRAINT fk_price_part FOREIGN KEY (part_id)
        REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. 생성된 견적 (요청당 3종: 가성비/안정성/최고성능)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quotes (
    quote_id    BIGINT      NOT NULL AUTO_INCREMENT,
    request_id  BIGINT      NOT NULL,
    tier        ENUM('가성비','안정성','최고성능') NOT NULL,
    total_price INT         NULL,
    reason      TEXT        NULL,       -- AI 자연어 추천 사유
    ai_model    VARCHAR(50) NULL,       -- gemini-2.5-flash 등
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (quote_id),
    UNIQUE KEY uk_quote_request_tier (request_id, tier),
    CONSTRAINT fk_quote_request FOREIGN KEY (request_id)
        REFERENCES survey_requests (request_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. 견적 구성 부품 (가격 스냅샷 박제)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quote_items (
    quote_item_id BIGINT NOT NULL AUTO_INCREMENT,
    quote_id      BIGINT NOT NULL,
    part_id       BIGINT NOT NULL,
    category_id   INT    NULL,
    unit_price    INT    NULL,          -- 생성 시점 가격 박제
    PRIMARY KEY (quote_item_id),
    KEY idx_qitem_quote (quote_id),
    CONSTRAINT fk_qitem_quote FOREIGN KEY (quote_id)
        REFERENCES quotes (quote_id) ON DELETE CASCADE,
    CONSTRAINT fk_qitem_part FOREIGN KEY (part_id)
        REFERENCES parts (part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 카테고리별 규격 (part_id = PK 겸 FK, 1:1 서브타입)  → 호환성 SQL 핵심
-- =====================================================================
CREATE TABLE IF NOT EXISTS cpu_specs (
    part_id   BIGINT      NOT NULL,
    socket    VARCHAR(20) NOT NULL,     -- AM5 / LGA1700
    tdp_watt  INT         NULL,
    cores     INT         NULL,
    threads   INT         NULL,
    has_igpu  BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (part_id),
    KEY idx_cpu_socket (socket),
    CONSTRAINT fk_cpu_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gpu_specs (
    part_id              BIGINT NOT NULL,
    vram_gb              INT    NULL,
    length_mm            INT    NULL,   -- 케이스 장착 길이 비교용
    tdp_watt             INT    NULL,
    recommended_psu_watt INT    NULL,   -- 파워 권장 출력 비교용
    PRIMARY KEY (part_id),
    CONSTRAINT fk_gpu_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mainboard_specs (
    part_id     BIGINT      NOT NULL,
    socket      VARCHAR(20) NOT NULL,   -- CPU 소켓 매칭
    form_factor ENUM('ATX','M-ATX','ITX') NOT NULL,  -- 케이스 폼팩터 매칭
    chipset     VARCHAR(50) NULL,
    ram_type    ENUM('DDR4','DDR5') NOT NULL,        -- RAM 규격 매칭
    ram_slots   INT         NULL,
    max_ram_gb  INT         NULL,
    m2_slots    INT         NULL,
    PRIMARY KEY (part_id),
    KEY idx_mb_compat (socket, form_factor, ram_type),
    CONSTRAINT fk_mb_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ram_specs (
    part_id     BIGINT NOT NULL,
    ram_type    ENUM('DDR4','DDR5') NOT NULL,
    capacity_gb INT    NULL,
    speed_mhz   INT    NULL,
    modules     INT    NULL,
    PRIMARY KEY (part_id),
    KEY idx_ram_type (ram_type),
    CONSTRAINT fk_ram_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS psu_specs (
    part_id     BIGINT      NOT NULL,
    watt        INT         NOT NULL,   -- 권장 출력 충족 비교용
    efficiency  VARCHAR(30) NULL,       -- 80+ Bronze/Gold/Platinum
    form_factor ENUM('ATX','SFX') NULL,
    PRIMARY KEY (part_id),
    KEY idx_psu_watt (watt),
    CONSTRAINT fk_psu_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cooler_specs (
    part_id      BIGINT NOT NULL,
    type         ENUM('공랭','수랭') NOT NULL,
    height_mm    INT    NULL,           -- 케이스 높이 비교용(공랭)
    tdp_rating   INT    NULL,
    radiator_mm  INT    NULL,           -- 라디에이터 규격(수랭)
    PRIMARY KEY (part_id),
    CONSTRAINT fk_cooler_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS storage_specs (
    part_id     BIGINT NOT NULL,
    `interface` ENUM('NVMe','SATA') NOT NULL,
    capacity_gb INT    NULL,
    form_factor ENUM('M.2','2.5inch') NULL,
    PRIMARY KEY (part_id),
    CONSTRAINT fk_storage_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS case_specs (
    part_id             BIGINT NOT NULL,
    max_gpu_length_mm   INT    NULL,     -- GPU 길이 상한
    max_cooler_height_mm INT   NULL,     -- 쿨러 높이 상한
    max_radiator_mm     INT    NULL,     -- 라디에이터 상한
    PRIMARY KEY (part_id),
    CONSTRAINT fk_case_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 다대다(M:N) 관계
-- =====================================================================
CREATE TABLE IF NOT EXISTS case_formfactor_support (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    part_id     BIGINT NOT NULL,        -- 케이스 part_id
    form_factor ENUM('ATX','M-ATX','ITX') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_ff (part_id, form_factor),
    CONSTRAINT fk_caseff_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cooler_socket_support (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    part_id BIGINT      NOT NULL,       -- 쿨러 part_id
    socket  VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cooler_socket (part_id, socket),
    KEY idx_cooler_socket (socket),
    CONSTRAINT fk_coolersock_part FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 시드: 부품 카테고리 8종
-- ---------------------------------------------------------------------
INSERT IGNORE INTO part_categories (code, name) VALUES
    ('CPU','CPU'), ('GPU','그래픽카드'), ('MB','메인보드'), ('RAM','메모리'),
    ('PSU','파워서플라이'), ('COOLER','쿨러'), ('STORAGE','저장장치'), ('CASE','케이스');
