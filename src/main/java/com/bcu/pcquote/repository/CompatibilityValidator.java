package com.bcu.pcquote.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 2차 가드레일: LLM 이 반환한 "조합"의 호환성을 DB 규격으로 재검증한다 (시연2 규칙).
 * enum 제약은 개별 부품이 후보군 안에 있게 하지만, 부품 간 호환(소켓/폼팩터/치수/전력)은 별도로 확인해야 한다.
 * 위반 목록을 반환하며, 빈 목록이면 호환 OK.
 */
@Repository
public class CompatibilityValidator {

    private final NamedParameterJdbcTemplate jdbc;

    public CompatibilityValidator(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 외장 GPU 는 선택(LEFT JOIN). gpuId 가 null 이면 내장그래픽 구성으로 본다.
    private static final String SQL = """
            SELECT
              (cpu.socket = mb.socket) AS socket_ok,
              (mb.ram_type = ram.ram_type) AS ram_ok,
              EXISTS(SELECT 1 FROM case_formfactor_support cf
                     WHERE cf.part_id = :caseId AND cf.form_factor = mb.form_factor) AS formfactor_ok,
              EXISTS(SELECT 1 FROM cooler_socket_support css
                     WHERE css.part_id = :coolerId AND css.socket = cpu.socket) AS cooler_socket_ok,
              (cl.height_mm IS NULL OR cl.height_mm <= cs.max_cooler_height_mm) AS cooler_height_ok,
              cpu.has_igpu AS has_igpu,
              (gpu.part_id IS NOT NULL) AS gpu_present,
              (gpu.length_mm <= cs.max_gpu_length_mm) AS gpu_len_ok,
              (psu.watt >= gpu.recommended_psu_watt) AS psu_ok
            FROM cpu_specs cpu
            JOIN mainboard_specs mb ON mb.part_id = :mbId
            JOIN ram_specs ram      ON ram.part_id = :ramId
            JOIN psu_specs psu      ON psu.part_id = :psuId
            JOIN cooler_specs cl    ON cl.part_id = :coolerId
            JOIN case_specs cs      ON cs.part_id = :caseId
            LEFT JOIN gpu_specs gpu ON gpu.part_id = :gpuId
            WHERE cpu.part_id = :cpuId
            """;

    public List<String> findViolations(Long cpuId, Long gpuId, Long mbId, Long ramId,
                                       Long psuId, Long coolerId, Long caseId) {
        // GPU 는 선택 → 필수에서 제외
        if (anyNull(cpuId, mbId, ramId, psuId, coolerId, caseId)) {
            return List.of("호환성 검증 불가: 필수 부품(CPU/메인보드/RAM/파워/쿨러/케이스) 누락");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cpuId", cpuId).addValue("gpuId", gpuId).addValue("mbId", mbId)
                .addValue("ramId", ramId).addValue("psuId", psuId)
                .addValue("coolerId", coolerId).addValue("caseId", caseId);

        List<List<String>> rows = jdbc.query(SQL, params, (rs, i) -> {
            List<String> v = new ArrayList<>();
            if (!rs.getBoolean("socket_ok"))         v.add("CPU-메인보드 소켓 불일치");
            if (!rs.getBoolean("ram_ok"))            v.add("메인보드-RAM 규격 불일치");
            if (!rs.getBoolean("formfactor_ok"))     v.add("케이스가 메인보드 폼팩터 미지원");
            if (!rs.getBoolean("cooler_socket_ok"))  v.add("쿨러가 CPU 소켓 미지원");
            if (!rs.getBoolean("cooler_height_ok"))  v.add("쿨러 높이가 케이스 한계 초과");

            boolean gpuPresent = rs.getBoolean("gpu_present");
            if (gpuPresent) {
                // 외장 GPU 사용 시: 길이/파워 검사
                if (!rs.getBoolean("gpu_len_ok"))    v.add("GPU 길이가 케이스 한계 초과");
                if (!rs.getBoolean("psu_ok"))        v.add("파워 용량이 GPU 권장출력 미만");
            } else {
                // 외장 GPU 미사용 시: CPU 내장그래픽 필수
                if (!rs.getBoolean("has_igpu"))      v.add("외장 GPU 가 없는데 CPU 내장그래픽도 없음");
            }
            return v;
        });

        return rows.isEmpty()
                ? List.of("호환성 검증 불가: 부품 규격 조회 실패")
                : rows.get(0);
    }

    private boolean anyNull(Long... ids) {
        for (Long id : ids) {
            if (id == null) {
                return true;
            }
        }
        return false;
    }
}
