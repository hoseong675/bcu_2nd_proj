# 타당성 검증 실행 로그

> 검증일: 2026-07-02 · 서버: `admin@100.91.60.40` (Pi 5, Tailscale)
> 요약/결론은 [index.md](./index.md) 참고
> ※ 실행 명령의 API 키는 보안상 `$GKEY` 로 마스킹함.

---

## 1. SSH 연결 및 OS 확인

```bash
ssh admin@100.91.60.40 'uname -a; cat /etc/os-release'
```
```
Linux pi5 6.8.0-1057-raspi #61-Ubuntu SMP ... aarch64
Ubuntu 24.04.4 LTS (Noble Numbat)
```

## 2. 서버 사양 및 스택

```bash
lscpu; free -h; df -h /; lsblk; java/mysql/python3/node/docker --version; tailscale ip -4
```
```
CPU   : Cortex-A76 x4 (aarch64)
RAM   : 7.8Gi total (6.7Gi free)
DISK  : /dev/nvme0n1p2  235G (222G 여유, 2% 사용)
STORAGE: nvme0n1 238.5G  KBG30ZMV256G TOSHIBA (NVMe)
설치됨 : Python 3.12.3, Node v24.17.0
미설치 : java, mysql, docker
Tailscale: 100.91.60.40
```

## 3. Gemini API 도달성 + 지연 (키 없이)

```bash
curl -s -o /dev/null -w "..." "https://generativelanguage.googleapis.com/v1beta/models"
```
```
DNS 정상 (IPv6): 2001:4860:4802:...::223
HTTP=403  DNS=0.007s  Connect=0.045s  TLS=0.095s  Total=0.16s
→ 403 "use API Key" = 연결 정상, 인증만 필요
반복 측정: 0.162 / 0.165 / 0.166s
```

## 4. 사용 가능 모델 목록 (키 인증 후)

```bash
curl -s "https://generativelanguage.googleapis.com/v1beta/models?key=$GKEY"
```
```
gemini-2.5-flash, gemini-2.5-pro, gemini-2.0-flash,
gemini-2.0-flash-lite, gemini-2.5-flash-lite,
gemini-flash-latest, gemini-pro-latest, ... (generateContent 지원)
```

## 5. 실전 견적 생성 테스트

요청 구성: 4K 영상편집용 / 예산 200만원 / 호환 부품 후보군(CPU·GPU·RAM·MB·PSU·SSD)을
구조화 JSON으로 전달 + `responseSchema`로 3종 견적(tier/cpu/gpu/.../reason) 강제.

```bash
curl -s -X POST ".../models/gemini-2.5-flash:generateContent?key=$GKEY" -d @req.json
```

### 5-1. 결과 (품질) — 정상
```
[가성비]  160만원  CPU:라이젠7 7700    / GPU:RTX4060Ti 16G
[안정성]  193만원  CPU:라이젠7 7700    / GPU:RTX4070S
[최고성능] 194만원  CPU:라이젠9 7900X   / GPU:RTX4070S
→ 후보군 내 부품만 사용, 예산 준수, 자연어 사유 포함
토큰: 입력 266 / 출력 534 / 총 5302
```

### 5-2. 지연시간 (기본값)
```
Total = 24.68s   ← 총 5302 - 입력266 - 출력534 = 약 4,500 thinking 토큰이 원인
```

## 6. 지연시간 최적화 비교 (thinking OFF)

`generationConfig.thinkingConfig.thinkingBudget = 0` 추가 후 동일 요청 재실행.

```
2.5-flash (thinking OFF)      -> 3.76s  | builds:3 | 총 766 토큰   ✅
2.5-flash-lite (thinking OFF) -> 8.79s  | builds:3 | 총 876 토큰
2.5-flash-lite (재시도)        -> 503 UNAVAILABLE (high demand)
2.0-flash                     -> 429 RESOURCE_EXHAUSTED (quota exceeded)
```

### 관찰
- thinking OFF 시 **24.7s → 3.8s** (품질 동일, builds 3종 유지).
- 무료 티어에서 **429/503가 실제로 재현됨** → Rate Limit이 유일한 실질 제약.

## 7. 도출된 권장 설정

```json
{
  "generationConfig": {
    "responseMimeType": "application/json",
    "responseSchema": { "...3종 견적 스키마..." },
    "thinkingConfig": { "thinkingBudget": 0 }
  }
}
```
- 주 모델 `gemini-2.5-flash`, 폴백 `gemini-2.5-flash-lite`
- 429/503 지수 백오프 재시도 필수
- 가격은 DB 캐싱, AI는 사용자 요청 시에만 호출

## 8. 조치 필요 (보안)

- 사용한 API 키가 채팅/터미널 히스토리에 노출됨 → **폐기 후 재발급**.
- 키는 Spring Boot 서버 환경변수로만 관리, 프론트엔드 노출 금지.
