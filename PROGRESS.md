# AI 기반 커스텀 PC 견적 추천 서비스 — 개발 진행 기록

> 학부 2학년 융합프로젝트(3인) · 최종 갱신: 2026-07-02
> 저장소: github.com/hoseong675/bcu_2nd_proj · 공개 데모: https://pi5.tail800aef.ts.net/

---

## 1. 프로젝트 개요

사용자의 용도·예산 설문을 받아, **DB 룰 기반 호환성 검증**을 거친 부품 후보군을 **생성형 AI(Gemini)**에 넘겨
**가성비·안정성·최고성능·내장그래픽 4종 견적**과 자연어 추천 사유를 제공하는 서비스.

핵심 설계 원칙: **룰(SQL) + AI를 결합해 할루시네이션을 차단**하고, 실제로 조립 가능한 견적만 추천한다.

---

## 2. 시스템 아키텍처 / 파이프라인

```
[설문 입력]
   ↓
[1차: 룰 기반 후보군 필터링]  ← SQL (호환 규격 + 최근 2년 + 최저가)
   ↓
[2차: enum 하드 제약 AI 견적 생성]  ← Gemini responseSchema(후보 part_id enum)
   ↓
[3차: 조합 호환성 재검증]  ← 위반 시 재구성(최대 3회), 끝까지 위반이면 제외
   ↓
[예산 후처리 · 실가격 합계 계산]
   ↓
[견적 4종 반환 → 프론트]

        ┌ 가격 캐싱 배치(네이버 쇼핑 API) → part_prices (일 배치/수동)
```

---

## 3. 기술 스택 & 인프라

| 구분 | 내용 |
|---|---|
| 서버 | Raspberry Pi 5 (8GB RAM, NVMe 256GB), Ubuntu 24.04 |
| 네트워크 | Tailscale (사설 `100.91.60.40`) + **Funnel 공개 HTTPS** |
| 백엔드 | **Spring Boot 4.1.0 / Java 21** (Gradle), Jackson 3(`tools.jackson`) |
| DB | MySQL 8.0.46 (InnoDB 버퍼풀 2GB, utf8mb4, DB `pc_quote`) |
| AI | Google AI Studio **Gemini 2.5-flash** (+ flash-lite 폴백) |
| 가격 | 네이버 쇼핑 검색 API |
| 프론트 | 정적 HTML + 바닐라 JS (Spring 정적 서빙) |
| 배포 | **systemd 서비스 `pcquote`** (부팅 자동/크래시 재시작), 시크릿은 `/etc/pcquote/pcquote.env`(600) |

---

## 4. 데이터베이스 (요약)

- **17개 테이블 + 2개 M:N** — 회원/설문/견적 + 부품 마스터 + **카테고리별 규격 서브타입**(cpu/gpu/mainboard/ram/psu/cooler/storage/case_specs) + 가격 캐싱.
- 규격을 카테고리별 테이블로 분리해 **호환성 조인이 타입 안전**하도록 설계.
- 상세: `schema.md`, DDL: `schema.sql`, 시드: `sample_data.sql`

**호환성 규칙 → SQL 매핑**: 소켓 일치 · 램 규격 일치 · 케이스 폼팩터 지원 · GPU 길이 · 쿨러 소켓/높이 · 파워 권장출력 · 최근 2년.

---

## 5. 구현 완료 기능 (시간순)

1. **타당성 검증** — Pi에서 Gemini 연결·구조화 JSON 견적 생성 실증. thinkingBudget=0으로 24초→3.8초.
2. **DB 스키마 설계·적용** — ERD/릴레이션 스키마 → MySQL DDL, 호환성 SQL 시연(비호환 부품 정확히 배제).
3. **Spring Boot 골격** — `POST /api/quotes`, 후보군 조회(JdbcTemplate), Gemini 호출(RestClient), 오케스트레이션.
4. **1차 가드레일 (enum 하드 제약)** — responseSchema의 각 `*_part_id`를 후보 id enum으로 제약 → 후보군 밖 부품 생성 차단.
5. **2차 가드레일 (조합 재검증)** — 반환 조합을 DB 규격으로 재검증(`CompatibilityValidator`).
6. **가격 배치 (네이버)** — 40종 부품 최저가 캐싱, `@Scheduled` 새벽 배치 + `POST /api/admin/price-batch`.
7. **가격 정제 + 예산 후처리** — 악세서리 극단 저가 컷 + `naver_query` 오버라이드 훅, **총액=캐싱 실가격 합계**, `withinBudget` 플래그.
8. **프론트엔드** — 설문 폼 → 4종 견적 카드(예산/호환 배지, 부품표, 사유).
9. **공개 배포 + 보안 + 상시화** — Tailscale Funnel 공개, `AdminAuthFilter`(X-Admin-Token), systemd 서비스화.
10. **내장그래픽 견적 추가** — 사용자에게 묻지 않고 4번째 선택지로 항상 제시(초보자용 설명). 외장 GPU를 스키마상 선택(nullable)화.
11. **Gemini 429/503 재시도·폴백** — 지수 백오프 후 flash-lite 폴백.
12. **호환성 위반 견적 제외** — 위반 견적은 위반 내용 피드백으로 재구성(최대 3회), 끝까지 위반이면 추천에서 배제.
13. **용도 기반 적정 사양(right-sizing)** — 사무/문서는 외장 GPU 미포함 등 워크로드에 맞게 스펙 축소.

### 진행 중 (미배포)
- **DDR4 저가 경로** — 인텔 14세대는 DDR4/DDR5 보드 모두 호환 → DDR5 고가 대응용 인텔+DDR4 조합 추가(카탈로그 6종 + 프롬프트 안내). *코드 수정 완료, 빌드·배포·검증 대기.*

---

## 6. 할루시네이션 / 오추천 방지 — 3중 방어

| 층 | 메커니즘 | 막는 것 |
|---|---|---|
| 1 | 룰 기반 후보군 필터링(SQL) | 애초에 비호환/구형 부품을 후보에서 제외 |
| 2 | responseSchema enum 하드 제약 | AI가 후보군에 없는 부품 선택 불가 |
| 3 | 조합 호환성 재검증 + 재구성/제외 | 소켓 불일치 등 조합 오류를 사용자에게 노출 안 함 |

**실사례**: Gemini가 최고성능 견적에 소켓 불일치를 생성 → 시스템이 감지·재구성 → 최종 견적 전부 호환 통과.

---

## 7. 검증 결과 (주요)

- **호환성 필터링**: DDR4 램/소켓 불일치/ITX 케이스 등 비호환 조합을 SQL이 정확히 배제.
- **AI 견적**: 4종 생성, 후보군 내 부품만, 예산·호환 플래그 정확.
- **가격 배치**: 40/40 실가격 캐싱, 견적에 반영.
- **재시도/폴백**: flash 429×3 → flash-lite 폴백 성공 확인.
- **적정 사양**: 사무용(엑셀/웹) 요청 시 4종 모두 외장 GPU 없이 구성됨(이전엔 4060 강제 포함).

---

## 8. 배포 / 운영

- **공개 URL**: https://pi5.tail800aef.ts.net/
- **관리 명령**
  - 상태/로그: `systemctl status pcquote` · `journalctl -u pcquote -f`
  - 재시작: `sudo systemctl restart pcquote`
  - 가격 배치: `curl -X POST localhost:8080/api/admin/price-batch -H "X-Admin-Token: <토큰>"`
- **재배포 플로우**: 로컬 수정 → `rsync -az --delete src/ admin@100.91.60.40:/home/admin/pcquote/src/` → `./gradlew bootJar` → `sudo systemctl restart pcquote`
- **시크릿**: `GEMINI_API_KEY / DB_* / NAVER_* / ADMIN_TOKEN` 은 `/etc/pcquote/pcquote.env`(600)에만. 코드/깃엔 없음.

---

## 9. 알려진 한계

- **가격 정확도**: 이름 기반 네이버 검색은 변형모델/번들/악세서리 혼입으로 일부 부품 가격이 부정확(특히 RAM). 근본 해결은 부품별 상품ID/`naver_query` 큐레이션.
- **DDR5 시세**: 실제로 DDR5가 고가라 저가 사무용 구성에 제약 → DDR4 경로로 대응 중.
- **Gemini 무료 티어**: 간헐적 429/503 → 재시도/폴백으로 완화하나 과부하 시 견적 수가 줄 수 있음.
- **공개 API 비용**: 견적 API는 무인증 공개 → 호출당 Gemini 비용. 트래픽 증가 시 rate limit 필요.

---

## 10. 남은 작업 후보

- [ ] DDR4 저가 경로 배포·검증 (진행 중)
- [ ] 예산 초과 견적 자동 재구성/제외 (호환성 방식과 동일하게)
- [ ] 가격 정확도: 주요 부품 `naver_query`/상품ID 큐레이션
- [ ] 공개 견적 API rate limit
- [ ] 3D 조립 가이드 (향후 확장, readme 2.4단계)

---

## 11. 커밋 이력 (요약)

`chore 초기화` → `DB DDL` → `호환 필터 시연` → `Spring Boot 골격` → `enum 제약` → `2차 가드레일` →
`카탈로그 40종` → `네이버 배치` → `가격정제+예산후처리` → `프론트` → `admin 인증` →
`내장그래픽+재시도폴백` → `위반 견적 제외` → `용도 적정사양` → (DDR4 경로: 진행 중)
