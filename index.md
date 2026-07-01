# Gemini API 기반 AI 견적 추천 — 타당성 검증 결과 (요약)

> 검증일: 2026-07-02 · 대상 서버: Raspberry Pi 5 (`admin@100.91.60.40`, Tailscale)
> 상세 실행 기록은 [log.md](./log.md) 참고

---

## 결론: **타당함 — 프로젝트 진행 가능** ✅

Google AI Studio(Gemini) API를 이용한 3종 견적 추천은 기술적으로 충분히 구현 가능하며,
readme의 "룰 기반 필터링 + 생성형 AI" 설계와 정확히 부합함을 실전 테스트로 확인했다.

---

## 1. 서버 환경

| 항목 | 상태 |
|---|---|
| 하드웨어 | Pi 5, Cortex-A76 4코어, RAM 7.8GB, NVMe 256GB(222GB 여유) |
| OS | Ubuntu 24.04.4 LTS (aarch64) |
| Tailscale | ✅ `100.91.60.40` 정상 |
| 설치됨 | Python 3.12, Node 24 |
| 미설치 (구축 필요) | Java, MySQL, Docker |

## 2. Gemini API 연결

- DNS/TLS/HTTPS 도달 정상 (IPv6 라우팅)
- 네트워크 왕복 오버헤드 **~0.16초** (UX 영향 없음)
- 최신 모델 접근 가능: `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash-lite` 등

## 3. 실전 견적 생성 테스트 (핵심)

`responseSchema`로 3종 견적 JSON을 강제한 결과:
- 후보군에 없는 부품을 지어내지 않음 (**할루시네이션 방지 확인**)
- 예산(200만원) 준수, 가성비/안정성/최고성능 3종 + 자연어 사유 정확히 반환

| 모델 / 설정 | 지연시간 | 비고 |
|---|---|---|
| 2.5-flash (thinking 기본 ON) | 24.7초 | 느림 — 추론 토큰 ~4,500개 |
| **2.5-flash (thinking OFF)** | **3.8초** ✅ | 품질 동일, 권장 |
| 2.5-flash-lite (thinking OFF) | 8.8초 | 503 혼잡 발생 |
| 2.0-flash | — | 429 쿼터 초과 |

## 4. 핵심 판단 근거

1. **연산 부담이 Pi에 없음** — 추론은 전부 구글 클라우드, Pi는 오케스트레이터 역할.
2. **지연시간 해결됨** — `thinkingConfig:{thinkingBudget:0}` 한 줄로 24초 → 3.8초.
3. **구조화 출력 적합** — `responseMimeType:application/json` + `responseSchema`로 정형 응답 강제.

## 5. 유일한 실질 제약 — 무료 티어 Rate Limit ⚠️

- 테스트 중 실제로 **429(쿼터 초과)·503(혼잡)** 발생.
- 무료 티어는 분당 요청수(RPM)/일일 한도가 빡빡함.
- 완화: 가격은 DB 캐싱하고 AI는 사용자 요청 시에만 호출 → 데모·소규모엔 무료로 충분.

## 6. 권장 구성

```
모델   : gemini-2.5-flash
설정   : thinkingConfig.thinkingBudget = 0
출력   : responseMimeType = application/json + responseSchema (3종 견적 스키마)
호출   : Spring Boot WebClient, 429/503 지수 백오프 재시도
폴백   : gemini-2.5-flash-lite
```

## 7. 보안 주의

- API 키는 **프론트엔드에 절대 노출 금지** — Spring Boot 서버 환경변수로만 관리.
- 검증에 사용한 키는 채팅/터미널 히스토리에 노출됐으므로 **폐기 후 재발급 권장**.

## 다음 단계

- [ ] 서버에 Java(JDK) + MySQL 설치
- [ ] Spring Boot Gemini 호출 서비스 골격 작성 (WebClient + 재시도)
- [ ] 부품 호환성 DB 스키마 설계
- [ ] API 키 rotate 및 환경변수 주입
