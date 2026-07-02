package com.bcu.pcquote.repository;

import com.bcu.pcquote.dto.CandidatePart;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 룰 기반 호환성 필터링 (readme 2.1단계).
 * 카테고리별 규격 테이블을 조인해, 호환성 판단에 필요한 규격을 요약한 후보 부품 Pool 을 반환한다.
 * - 최신성: 출시 2년 이내
 * - 케이스/쿨러는 지원 폼팩터/소켓을 GROUP_CONCAT 으로 함께 노출 → LLM 이 조합 호환성 판단 가능
 * - 최저가는 part_prices(is_lowest) LEFT JOIN
 */
@Repository
public class CompatibilityRepository {

    private final JdbcTemplate jdbc;

    public CompatibilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String RECENT = " p.is_active = 1 AND p.release_date >= DATE_SUB(CURDATE(), INTERVAL 2 YEAR) ";
    private static final String PRICE_JOIN = " LEFT JOIN part_prices pr ON pr.part_id = p.part_id AND pr.is_lowest = 1 ";

    private static final String SQL = """
            SELECT 'CPU' AS category, p.part_id, p.manufacturer, p.model_name,
                   CONCAT('소켓 ', s.socket, ', ', s.cores, '코어/', s.threads, '스레드, TDP ', s.tdp_watt, 'W',
                          IF(s.has_igpu, ', 내장그래픽 있음(외장GPU 없이 구성 가능)', ', 내장그래픽 없음(외장GPU 필수)')) AS spec, pr.price
            FROM parts p JOIN cpu_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'GPU', p.part_id, p.manufacturer, p.model_name,
                   CONCAT('VRAM ', s.vram_gb, 'GB, 길이 ', s.length_mm, 'mm, 권장파워 ', s.recommended_psu_watt, 'W'), pr.price
            FROM parts p JOIN gpu_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'MAINBOARD', p.part_id, p.manufacturer, p.model_name,
                   CONCAT('소켓 ', s.socket, ', ', s.form_factor, ', ', s.ram_type, ', 최대 ', s.max_ram_gb, 'GB'), pr.price
            FROM parts p JOIN mainboard_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'RAM', p.part_id, p.manufacturer, p.model_name,
                   CONCAT(s.ram_type, ' ', s.capacity_gb, 'GB(', s.modules, '개), ', s.speed_mhz, 'MHz'), pr.price
            FROM parts p JOIN ram_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'PSU', p.part_id, p.manufacturer, p.model_name,
                   CONCAT(s.watt, 'W, ', s.efficiency), pr.price
            FROM parts p JOIN psu_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'COOLER', p.part_id, p.manufacturer, p.model_name,
                   CONCAT(s.type,
                          COALESCE(CONCAT(', 높이 ', s.height_mm, 'mm'), ''),
                          ', 지원소켓 ', (SELECT GROUP_CONCAT(css.socket) FROM cooler_socket_support css WHERE css.part_id = p.part_id),
                          ', TDP ', s.tdp_rating, 'W'), pr.price
            FROM parts p JOIN cooler_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'STORAGE', p.part_id, p.manufacturer, p.model_name,
                   CONCAT(s.`interface`, ' ', s.capacity_gb, 'GB, ', s.form_factor), pr.price
            FROM parts p JOIN storage_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT + """
            UNION ALL
            SELECT 'CASE', p.part_id, p.manufacturer, p.model_name,
                   CONCAT('지원폼팩터 ', (SELECT GROUP_CONCAT(cf.form_factor) FROM case_formfactor_support cf WHERE cf.part_id = p.part_id),
                          ', 최대 GPU ', s.max_gpu_length_mm, 'mm, 최대 쿨러 ', s.max_cooler_height_mm, 'mm'), pr.price
            FROM parts p JOIN case_specs s ON s.part_id = p.part_id
            """ + PRICE_JOIN + " WHERE " + RECENT;

    public List<CandidatePart> findCandidatePool() {
        return jdbc.query(SQL, (rs, rowNum) -> new CandidatePart(
                rs.getString("category"),
                rs.getLong("part_id"),
                rs.getString("manufacturer"),
                rs.getString("model_name"),
                rs.getString("spec"),
                (Integer) rs.getObject("price")
        ));
    }
}
