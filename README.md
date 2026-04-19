# 수강 신청 시스템

온라인 라이브 클래스의 수강 신청 시스템 백엔드 API.

## 프로젝트 개요

크리에이터(강사)가 강의를 개설하고, 클래스메이트(수강생)가 신청·결제·취소하는 전 과정을 처리한다.

**핵심 비즈니스 규칙**
- 강의 상태 머신: `DRAFT → OPEN → CLOSED`
- 수강 신청 상태 머신: `PENDING → CONFIRMED → CANCELLED`
- 정원 초과 시 신청 거부, 동시 신청은 비관적 락으로 직렬화
- CONFIRMED 이후 7일 이내에만 수강 취소 가능 (환불 이력 기록)
- 정원이 찬 강의에는 대기열 등록 가능, 취소 발생 시 선두 자동 승격

## 기술 스택

- Java 21
- Spring Boot 3.3
- PostgreSQL 15
- Spring Data JPA + Flyway
- Gradle
- Docker / Docker Compose
- Swagger UI (springdoc-openapi)
- Testcontainers (JUnit 통합 테스트)

## 실행 방법

### 로컬

```bash
# 1. DB 기동
cd enrollment-infra
docker compose up -d

# 2. 앱 실행
cd ../enrollment-api
./gradlew bootRun

# 3. 헬스체크
curl http://localhost:8080/actuator/health
# {"status":"UP",...}

# 4. Swagger UI 접근
open http://localhost:8080/swagger-ui/index.html
```

### 시드 유저 (V3 마이그레이션)

앱 기동 시 Flyway가 자동으로 시드 유저 2명을 생성한다.

| user_id | role | 설명 |
|---------|------|------|
| 1 | CREATOR | 강사용 |
| 2 | CLASSMATE | 수강생용 |

`X-User-Id` 헤더로 식별하므로 리뷰 시 별도 가입 과정 없이 바로 API 호출 가능.

## API 목록 및 예시

### 강의 관리

| # | Method | Path | 권한 |
|---|--------|------|------|
| 1 | POST | `/classes` | 강사 |
| 2 | PATCH | `/classes/{id}` | 본인 강사 |
| 3 | PATCH | `/classes/{id}/publish` | 본인 강사 |
| 4 | PATCH | `/classes/{id}/close` | 본인 강사 |
| 5 | GET | `/classes?status=OPEN&page=0&size=20` | 누구나 |
| 6 | GET | `/classes/{id}` | 누구나 |
| 7 | GET | `/classes/me?page=0&size=20` | 강사 |
| 8 | GET | `/classes/{id}/enrollments?page=0&size=20` | 본인 강사 |

### 수강 신청 / 결제 / 취소

| # | Method | Path | 권한 |
|---|--------|------|------|
| 9 | POST | `/enrollments` | 수강생 |
| 10 | PATCH | `/enrollments/{id}/pay` | 본인 수강생 |
| 11 | PATCH | `/enrollments/{id}/cancel` | 본인 수강생 |
| 12 | GET | `/enrollments/me?page=0&size=20` | 수강생 |

### 대기열 (정원 초과 시)

| # | Method | Path | 권한 |
|---|--------|------|------|
| 13 | POST | `/classes/{id}/waitlist` | 수강생 |
| 14 | DELETE | `/classes/{id}/waitlist/me` | 본인 |

### 요청 예시

```bash
# 강의 등록 (CREATOR)
curl -X POST http://localhost:8080/classes \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Java 기초",
    "description": "자바 입문 강의",
    "price": 50000,
    "capacity": 10,
    "startDate": "2026-06-01",
    "endDate": "2026-06-30"
  }'

# 강의 공개
curl -X PATCH http://localhost:8080/classes/1/publish -H "X-User-Id: 1"

# 수강 신청 (CLASSMATE)
curl -X POST http://localhost:8080/enrollments \
  -H "X-User-Id: 2" \
  -H "Content-Type: application/json" \
  -d '{"classId": 1}'

# 결제
curl -X PATCH http://localhost:8080/enrollments/1/pay -H "X-User-Id: 2"

# 취소
curl -X PATCH http://localhost:8080/enrollments/1/cancel -H "X-User-Id: 2"

# 대기열 등록 (정원 초과 시)
curl -X POST http://localhost:8080/classes/1/waitlist -H "X-User-Id: 3"
```

### 에러 응답 포맷

```json
{
  "code": "CAPACITY_EXCEEDED",
  "message": "정원이 초과되었습니다",
  "timestamp": "2026-04-19T14:00:00",
  "path": "/enrollments"
}
```

## 데이터 모델 설명

### 테이블

| 테이블 | 책임 |
|--------|------|
| `users` | 사용자 식별 + 역할 구분 (CREATOR / CLASSMATE) |
| `classes` | 강의 정보 + 현재 신청 수(`enrolled_count`, 역정규화 + 락 대상) |
| `enrollments` | 수강 신청 이력 + 상태 (PENDING / CONFIRMED / CANCELLED) |
| `payments` | 결제 이력 (append-only, PAID / REFUNDED / FAILED) |
| `waitlists` | 대기열 (WAITING / PROMOTED / CANCELLED) |

### 연관 관계 (모두 단방향 `@ManyToOne`)

- User → Class(`created_by` FK) : 강사가 여러 강의를 개설
- User → Enrollment(`user_id` FK) : 수강생이 여러 신청
- Class → Enrollment(`class_id` FK)
- Enrollment → Payment(`enrollment_id` FK)
- Class → Waitlist, User → Waitlist
- Waitlist → Enrollment(`promoted_enrollment_id` FK, 승격 시에만)

양방향 매핑은 JSON 순환 참조 + N+1 위험이 커서 역참조는 모두 Repository 쿼리로 명시적 처리.

### 상태 머신

```
Class:       DRAFT  ──publish──▶  OPEN  ──close──▶  CLOSED
Enrollment:  PENDING ──pay──▶ CONFIRMED ──cancel(7일 이내)──▶ CANCELLED
                │                                                ▲
                └──────cancel(결제 전 자유)──────────────────────┘
Waitlist:    WAITING ──promote──▶ PROMOTED (terminal)
                │
                └──cancel──▶ CANCELLED (terminal, 재등록은 새 row)
Payment:     append-only (상태 전이 없음, 각 row 독립)
```

## 요구사항 해석 및 가정

1. **인증은 X-User-Id 헤더로 단순화**. Spring Security / JWT는 범위 밖. 실서비스에선 게이트웨이에서 주입되는 구조를 가정
2. **좌석 차감 시점**: 수강 신청(PENDING 생성) 시점. 결제 이전이라도 좌석을 즉시 점유(홀드)하여 "결제 완료 후 좌석 없음" 상황 방지
3. **결제 시뮬레이션**: 외부 PG 연동 없이 `PATCH /enrollments/{id}/pay` 호출 시 즉시 `PAID` row를 INSERT. `pg_transaction_id`, 콜백 등은 미구현
4. **취소 가능 기간**: CONFIRMED 이후 7일을 상수로 박음. 강의별 정책은 추후 `ClassEntity`로 이동 가능
5. **사용자 역할**: 한 User가 CREATOR 겸 CLASSMATE일 수 있음. `role` 컬럼은 힌트이고 실제 권한은 리소스 소유권(FK) 기반 판단
6. **강사 본인 강의 수강**: 허용. 요구사항에 금지 조항 없음
7. **수강 취소 후 재신청**: 허용. 중복 신청 방지 인덱스는 활성 상태(`PENDING`, `CONFIRMED`)에만 적용
8. **환불 처리**: CONFIRMED 상태에서 취소 시 `payments` 테이블에 `REFUNDED` row 자동 INSERT. 실제 금전 환불은 PG 영역이라 이력만 기록
9. **DELETE 메서드 부재**: 모든 삭제는 상태 전이로 대체하여 이력 보존

## 설계 결정과 이유

### 1. 좌석 차감 타이밍 — 신청 시점(PENDING)
- 결제 시점 차감이면 "결제 완료 후 좌석 없음" 경쟁이 발생
- 여러 사용자가 PENDING 상태에서 동시 결제 시도 시 한 명만 성공, 나머지는 환불 지옥
- 동시성 제어 지점을 **신청 API 1곳**으로 단일화

### 2. 동시성 제어 — 비관적 락 `SELECT ... FOR UPDATE`
- 인기 강의는 충돌 빈도가 높아 낙관적 락(`@Version`)은 재시도 비용 큼
- `classes` row 단일 락으로 신청/취소/대기 승격 전부 직렬화
- 10-스레드 동시 신청 시나리오 테스트(`EnrollmentConcurrencyTest`, `WaitlistConcurrencyTest`)로 검증

### 3. 역정규화 — `classes.enrolled_count`
- 강의 목록 조회 시 매번 `SELECT COUNT(*) FROM enrollments`는 N+1
- `enrolled_count` 컬럼 유지 + `CHECK (enrolled_count <= capacity)` DB 레벨 불변식
- 3중 방어: 서비스 체크 + 엔티티 도메인 메서드 + DB CHECK

### 4. 중복 신청 방지 — PostgreSQL 부분 유니크 인덱스
```sql
CREATE UNIQUE INDEX uk_active_enrollment
    ON enrollments(class_id, user_id)
    WHERE status IN ('PENDING', 'CONFIRMED');
```
- 취소 후 재신청은 허용(CANCELLED는 인덱스 밖), 동일 강의 중복 활성 신청은 차단
- MySQL이었다면 Generated Column 우회 필요 — PostgreSQL 선택으로 스키마 단순화

### 5. 결제는 append-only
- `payments.status`는 전이 없이 새 row INSERT
- 결제 완료: `PAID` INSERT, 환불: `REFUNDED` INSERT, 실패: `FAILED` INSERT (현재 미구현)
- `uk_payment_paid WHERE status='PAID'` 부분 유니크 인덱스로 이중 결제 DB 차단
- 감사(audit)/환불 추적이 쉬움

### 6. 대기열 — 수동 등록 + 자동 승격
- 정원 초과 응답(`CAPACITY_EXCEEDED`) 받은 클라이언트가 명시적으로 `POST /classes/{id}/waitlist` 호출 (자동 대기 안 함)
- 수강 취소 발생 시 `EnrollmentService.cancel()` 말미에서 `WaitlistService.promoteNext()` 동기 호출 → 선두 자동 승격
- 선두가 이미 다른 경로로 활성 신청을 가지고 있으면 `skip` 후 다음 선두로 (방어 while 루프)
- 자동 승격 시 `Enrollment`는 `PENDING` 상태로 생성되어 승격자가 결제만 하면 수강 확정

### 7. 역할(Role) 검증 — 강의 등록에만 적용
- `POST /classes`는 `CREATOR` 역할만 허용 (`FORBIDDEN_ROLE`)
- 수정/공개/마감/수강생 목록 조회는 "본인 강의"(소유권) 검증만으로 충분
- 한 User가 CREATOR + CLASSMATE 모두 가능 (기획 가정)

## 테스트 실행 방법

```bash
cd enrollment-api

# 전체 테스트
./gradlew test

# 커버리지 리포트 (JaCoCo)
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### 테스트 구성 (총 212 tests)

| 계층 | 프레임워크 | 주요 대상 |
|------|----------|---------|
| Entity 단위 | JUnit 5 | 상태 머신, 도메인 메서드 검증 |
| Service 단위 | Mockito | 비즈니스 로직, 에러 경로 |
| Repository 통합 | `@DataJpaTest` + Testcontainers PostgreSQL | CHECK 제약, 부분 유니크 인덱스, JOIN FETCH |
| Controller 슬라이스 | `@WebMvcTest` + `@Import(GlobalExceptionHandler)` | HTTP 상태 / 에러 코드 매핑 |
| 동시성 통합 | `@SpringBootTest` + Testcontainers | 10-스레드 정원 경쟁, net-zero 불변식 |

Testcontainers가 Docker 컨테이너로 실제 PostgreSQL 15를 띄워 Flyway 마이그레이션(V1~V6)까지 실행하므로 프로덕션과 동일한 환경에서 검증.

## 미구현 / 제약사항

| 항목 | 이유 / 확장 방향 |
|------|----------------|
| **인증/인가 체계** | `X-User-Id` 헤더 신뢰. 실제 환경에선 Spring Security + JWT/게이트웨이로 대체 |
| **PG 실제 연동** | 외부 결제 시스템 범위 밖. `PAID` 상태 단순 기록. 확장 시 `PENDING_APPROVAL` 중간 상태 + 콜백 핸들러 + `pg_transaction_id` 컬럼 추가 |
| **PENDING TTL** | 결제 지연 시 좌석이 무한 홀드. 확장 시 `expires_at` 컬럼 + `@Scheduled` 1분 배치로 만료 CANCELLED + 다음 대기자 자동 승격 체인 |
| **대기열 승격 알림** | 이메일/푸시/WebSocket 등 통신 레이어는 범위 밖. 승격자는 `/enrollments/me` 조회로 PENDING 확인 후 결제 |
| **대기 순번 조회 API** | MVP에서 제외. 필요 시 `ROW_NUMBER() OVER` 기반 별도 엔드포인트 추가 |
| **다중 서버 확장** | 단일 인스턴스 기준 비관적 락. 수평 확장 시 Redis 분산락(Redisson) 추가 필요 |
| **강의 기간 종료 자동 마감** | `end_date` 경과 시 자동 CLOSED 전환 미구현. `@Scheduled` 자정 배치로 확장 가능 |
| **DRAFT 강의 삭제** | 물리 삭제 미지원. 현재 정책상 모든 삭제는 상태 전이(CANCELLED / CLOSED)로 대체 |

## AI 활용 범위

- **코드 생성**: Spring Boot 기본 구조, Repository/Service/Controller 레이어 보일러플레이트를 Claude Code CLI(Sonnet 4.6 / Opus 4.7)로 초안 생성 후 수정·검토
- **설계 검토**: 동시성 락 전략, 역정규화 구조, 부분 유니크 인덱스 사용 여부 등 의사결정 전에 AI와 trade-off 대화로 검증
- **테스트 시나리오 발굴**: 엣지 케이스(7일 경계값, 10-스레드 경쟁, 부분 유니크 인덱스 양방향) 후보 제안 및 리뷰
- **문서 정리**: README / 커밋 메시지 초안
- **제외**: 비즈니스 요구사항 해석과 최종 설계 결정은 직접 수행. AI 출력은 전부 수동 검토 후 반영

주요 프롬프트 패턴은 "X 대신 Y로 바꾸면 어떤 trade-off가 있나?", "이 코드에서 실무적으로 지적될 수 있는 부분은?" 같은 **비판/검토 질문**. 코드를 그대로 쓰지 않고 프로젝트 컨벤션에 맞게 재작성.
