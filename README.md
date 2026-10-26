# Next Portal Backend

데이터 포털의 백엔드 프로젝트입니다. 관리자 시스템을 먼저 구현하고 사용자 API와
데이터 수집·정제 Batch를 점진적으로 추가하는 Gradle 멀티 모듈 구조입니다.

## 기술 구성

- Java 21 / Spring Boot 4.1.2 / Gradle Wrapper
- Spring Data JPA / OpenFeign Querydsl 7.1 / Flyway / MariaDB
- Spring Security / JWT / Redis 세션과 Refresh 토큰 회전
- DTO·설정은 Record, 단순 주입 생성자·Entity getter는 Lombok으로 생성

## 모듈과 폴더

| 모듈 | 역할 | 단독 실행 |
|---|---|---|
| `common` | Entity, enum, JPA Repository, Querydsl 조회, Flyway 마이그레이션 | 아니요 |
| `api` | 공통 웹 오류, JWT, Redis 세션, Spring Security 라이브러리 | 아니요 |
| `admin` | 관리자 인증과 사용자 관리 API. 기본 포트 8080 | 예 |
| `user` | 사용자 API 시작점. 기본 포트 8081 | 예 |
| `batch` | Spring Batch 실행 프로젝트. 실제 Job은 후속 구현 | 예 |

```text
admin ─┐
       ├──→ api ──→ common
user ──┘
batch ────────────→ common
```

각 모듈의 `build.gradle`은 의존성과 빌드 설정을 정의하고, 루트 `settings.gradle`은
참여 모듈을 정의합니다. 관리자 서버를 실행하면 `api`와 `common`이 라이브러리로 포함됩니다.

- `config`: JAR 밖의 로컬 설정과 공유용 예시 YAML
- `.run`: IntelliJ 실행 설정
- `gradle`: Gradle Wrapper 파일
- `.gradle`, `build`, `out`, `src/main/generated`: 캐시·빌드·자동 생성 결과. Git 제외
- `docs/openapi.json`: 관리자 API 계약

프론트엔드는 이 저장소에 포함하지 않습니다.

## 로컬 실행

Java 21, 실행 중인 MariaDB와 Redis가 필요합니다. 로컬 설정은 Git에서 제외하므로
처음 클론한 뒤 한 번 생성하고 값을 입력하세요.

```powershell
.\scripts\init-local.ps1
```

`config/application-local.yml`에 DB URL·계정·비밀번호, Redis 주소, 허용 출처와 포트를
설정하세요. 초기화 스크립트가 로컬 JWT 키를 임의 생성합니다. 설정 예시는
`config/application-local.example.yml`에 있습니다.

MariaDB에는 `portal` 데이터베이스와 접근 가능한 계정을 준비해야 합니다. 앱 기동 시
Flyway가 테이블을 생성하고 Hibernate가 스키마를 검증합니다. 기존 Docker 컨테이너에
연결할 수 있으며 이 프로젝트가 컨테이너를 자동 생성하지는 않습니다.

최초 관리자를 만들려면 로컬 YAML의 `portal.bootstrap-admin.email/password`를 설정하세요.
비밀번호는 12자 이상, UTF-8 기준 72바이트 이하여야 합니다. 동일 이메일이 이미 있으면
기존 비밀번호를 변경하지 않습니다.

```powershell
# 이 저장소 루트에서 실행. 프로필 미지정 시 local.
.\gradlew.bat :admin:bootRun

# 사용자 API / Batch는 필요할 때 각각 실행
.\gradlew.bat :user:bootRun
.\gradlew.bat :batch:bootRun
```

macOS/Linux에서는 같은 명령의 `gradlew.bat` 대신 `./gradlew`를 사용하세요.
설정 파일이 없으면 예시 YAML을 `config/application-local.yml`로 복사하고 32바이트 이상의
임의 JWT 키와 DB 접속 정보를 직접 입력할 수도 있습니다.

IntelliJ에서는 이 저장소 루트를 Gradle 프로젝트로 열고 Java 21을 선택한 뒤 Gradle을
동기화하세요. `.run`의 `(local)` 실행 설정을 사용하거나 Working directory를 저장소 루트로
맞춰 main 메서드를 실행하면 됩니다. `bootRun`의 작업 디렉터리도 저장소 루트로 고정했습니다.
실제 로컬 YAML은 실행 JAR에 포함되지 않습니다.

## 운영 설정

공통 설정은 `common/src/main/resources/portal-common.yml`, 운영 환경변수 연결은
`common/src/main/resources/portal-prod.yml`에서 관리합니다. 운영에서는 `prod`를 명시합니다.

```powershell
.\gradlew.bat :admin:bootJar
java -jar admin/build/libs/admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

배포 환경에서 다음 환경변수를 전달하세요. 실제 비밀값을 소스에 작성하지 않습니다.

| 환경변수 | 용도 | 기본값 |
|---|---|---|
| `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB 연결 | 필수 |
| `DB_PORT` | DB 포트 | 3306 |
| `REDIS_HOST` | Redis 주소 | 필수 |
| `REDIS_PORT`, `REDIS_DATABASE` | Redis 포트·DB 번호 | 6379 / 0 |
| `REDIS_PASSWORD` | Redis 비밀번호 | 빈 값 |
| `JWT_SECRET` | 32바이트 이상 JWT 서명 키 | 필수 |
| `ALLOWED_ORIGINS` | 허용할 프론트 출처, 쉼표 구분 | 필수 |
| `ADMIN_PORT`, `USER_PORT` | 서버 포트 | 8080 / 8081 |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | 최초 관리자 생성 | 생성 안 함 |

`prod`는 로컬 YAML을 읽지 않으며 Refresh 쿠키에 `Secure`를 적용합니다.

## 인증과 관리자 기능

- 로그인·내 정보·토큰 재발급·로그아웃
- 사용자 등록·조회·검색·페이지 조회·역할/상태 필터·이름/역할/상태 수정
- BCrypt(12), 이메일 소문자 정규화, 입력 검증, 유일성 제약, 낙관적 잠금
- 자기 계정의 관리자 권한 해제·비활성화 차단

Access JWT는 기본 15분, Refresh 세션은 7일입니다. Redis에는 Access의 JTI, Refresh의
SHA-256 해시, 사용자 ID·인증 버전을 TTL과 함께 저장합니다. Refresh는 Lua로 원자적으로
회전하고, 갱신 시 이전 Access도 거부합니다. 로그아웃과 계정 역할·상태 변경은 기존 세션의
접근을 차단합니다. Refresh 토큰은 HttpOnly / SameSite=Strict 쿠키로 전달합니다.

로그인·Refresh API에는 허용 목록과 일치하는 `Origin` 헤더가 필요합니다. 로컬 기본값은
`http://localhost:3000`, `http://localhost:3001`입니다. 나머지 관리자 API는 Bearer JWT와
ADMIN 권한을 요구합니다. Redis 키는 `next-portal:session:{sid}` 형식입니다.

사용자 회원가입·로그인·데이터 검색, 실제 Batch Job과 메타데이터 DDL, 로그인 시도 제한,
비밀번호 재설정·감사 로그는 아직 구현하지 않았습니다.

## 작성 원칙과 검증

Record는 DTO·응답·설정에 유지합니다. 서비스·컨트롤러의 단순 주입은
`@RequiredArgsConstructor`, Entity는 필요한 `@Getter`와 보호된 `@NoArgsConstructor`만
사용합니다. `@Data`·`@Setter`는 `lombok.config`에서 차단합니다. Entity 생성·변경 로직은
직접 작성하고, 변환은 Stream, 오류 분류는 패턴 매칭 switch 표현식을 사용합니다.

초기에는 기능별 Controller → Service → Repository 구조를 사용합니다. 외부 데이터 공급자나
저장소 의존성이 복잡해지는 기능부터 Port/Adapter 도입을 검토합니다.

```powershell
.\gradlew.bat build

# 선택: localhost:6379의 실제 Redis에서 원자적 갱신·TTL·폐기 검증
$env:ENABLE_REDIS_TESTS='true'
.\gradlew.bat :api:test --rerun-tasks
```

관리자 통합 테스트는 Spring Security·JPA·Querydsl·Flyway와 H2 MariaDB 모드를 사용하고
Redis 저장소를 대체합니다. 선택 Redis 테스트는 테스트 전용 UUID 키만 사용하고 삭제합니다.
실제 MariaDB 연결은 별도로 기동해 확인하세요. MariaDB 13.0.2에서 초기 연결·마이그레이션을
검증했으나, 현재 Flyway는 MariaDB 11.7까지 검증되었다는 경고를 출력합니다.
