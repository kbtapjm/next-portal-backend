# 역할과 세부 권한

백엔드는 **사용자 → 여러 역할 → 세부 권한의 합집합**으로 접근을 판정합니다.
역할은 DB에서 관리하고, API 정책은 역할 이름이 아닌 `hasAuthority('user:read')` 같은
권한 코드로 검사합니다. 별도 권한 서버나 권한 캐시는 사용하지 않습니다.

## 저장 구조

```text
portal_member ─ portal_member_role ─ portal_role
                                      │
                             portal_role_permission
                                      │
                              portal_permission
```

`V2__add_roles_and_permissions.sql`이 기존 ADMIN/USER 값을 새 매핑 테이블에 이관한 뒤
기존 단일 `role` 컬럼을 제거합니다. 이메일·비밀번호·계정 상태는 유지합니다.
업데이트할 때는 구버전 관리자·사용자·Batch 앱을 먼저 중지하고 새 버전을 실행하세요.
마이그레이션 후 기존 로그인은 만료되므로 다시 로그인해야 합니다.

## 권한 카탈로그

| 코드 | 의미 |
|---|---|
| `admin:access` | 관리자 로그인과 `/api/admin/**` 접근 |
| `user:read` | 사용자 목록·상세 조회 |
| `user:write` | 사용자 생성, 기본 정보·상태 수정 |
| `role:read` | 역할과 권한 목록·상세 조회 |
| `role:manage` | 역할 생성·수정·삭제 및 사용자 역할 부여 |

관리자 API는 공통적으로 `admin:access`를 요구하고 서비스 메서드에서 작업별 권한을
추가 검사합니다. `role:manage`가 `user:write`나 `role:read`를 자동 포함하지는 않습니다.
필요한 권한을 역할에 명시적으로 묶으세요.

권한 코드는 실제 API 정책과 연결되어야 하므로 카탈로그 생성·수정 API는 제공하지
않습니다. 기능을 추가할 때 코드와 Flyway 마이그레이션으로 권한을 추가하고 ADMIN에도
명시적으로 부여하세요. 데이터셋 권한은 해당 API를 구현할 때 추가합니다.

## 초기 역할

| 코드 | 권한 | 변경·삭제 |
|---|---|---|
| `ADMIN` | 현재 구현된 모든 권한 | 시스템 역할, 불가 |
| `USER` | 관리자 권한 없음 | 시스템 역할, 불가 |
| `USER_VIEWER` | `admin:access`, `user:read` | 가능 |
| `USER_MANAGER` | `admin:access`, `user:read`, `user:write` | 가능 |

일반 USER 계정은 관리자 로그인이 거부됩니다. `admin:access`만 있는 역할은 로그인과
자신의 정보 조회·갱신·로그아웃을 할 수 있지만 사용자 목록은 조회할 수 없습니다.

## API 계약

| API | 필요한 추가 권한 |
|---|---|
| `GET /api/admin/auth/me` | 없음, 자신의 정보만 반환 |
| `GET /api/admin/users`, `GET /api/admin/users/{id}` | `user:read` |
| `POST /api/admin/users`, `PUT /api/admin/users/{id}` | `user:write` |
| `GET /api/admin/permissions` | `role:read` |
| `GET /api/admin/roles`, `GET /api/admin/roles/{code}` | `role:read` |
| `POST /api/admin/roles` | `role:manage` |
| `PUT /api/admin/roles/{code}`, `DELETE /api/admin/roles/{code}` | `role:manage` |
| `PUT /api/admin/users/{id}/roles` | `role:manage` |

사용자 생성 시 USER 이외의 역할 부여, 기존 사용자의 역할 변경, `role:manage`를 가진
계정의 기본 정보·상태 수정에는 `role:manage`도 필요합니다. `user:write`만으로 관리자
계정을 생성하거나 시스템 관리자를 비활성화할 수 없습니다.

역할을 생성하는 예시:

```json
{
  "code": "AUDITOR",
  "name": "감사 담당자",
  "permissions": ["admin:access", "user:read", "role:read"]
}
```

`PUT /api/admin/users/{id}/roles`로 여러 역할을 부여하는 예시:

```json
{ "roles": ["USER", "AUDITOR"] }
```

역할·권한 목록은 전체 교체 방식입니다. 역할의 `permissions: []`는 허용하지만
사용자의 `roles: []`는 거부합니다. 역할 코드는 대문자·숫자·밑줄, 영문 대문자로 시작하는
최대 50자이며 생성 후 변경할 수 없습니다. 사용자에게 부여 중인 역할은 회수 후 삭제합니다.

현재 사용자의 `GET /api/admin/auth/me` 응답에는 `roles`와 합산된 `permissions`가
포함됩니다. 프론트의 메뉴·버튼 표시에도 이 `permissions`를 사용할 수 있습니다.
화면 표시와 별개로 실제 권한 검사는 항상 백엔드에서 수행합니다.

## 기존 클라이언트 호환

사용자 생성·수정의 기존 `role: "ADMIN" | "USER"` 요청과 검색 필터는 유지합니다.
새 클라이언트에서는 `roles` 배열과 `roleCode` 검색 필터를 사용하세요.

- 생성: `roles` 생략 시 기존 `role`을 사용하고, 둘 다 없으면 USER를 부여합니다.
- 수정: `roles` 생략 시 역할을 유지합니다. 기존 `role`이 현재 호환용 분류와 다르면
  해당 ADMIN 또는 USER 역할 하나로 교체하며 `role:manage`가 필요합니다.
- `role`과 `roles`를 동시에 지정하면 400입니다.
- 응답의 기존 `role`은 `admin:access` 보유 시 ADMIN, 그 외 USER로 계산한 호환용 값입니다.
  실제 역할 저장·인가 판단의 기준은 아닙니다.
- 검색의 `role=ADMIN/USER`는 관리자 접근 가능 여부, `roleCode=USER_VIEWER`는 실제 역할입니다.
  두 필터를 같이 보내면 두 조건을 모두 만족해야 합니다.

## 권한 변경과 토큰

JWT에는 사용자 ID·세션 ID·인증 버전과 역할 권한 버전의 지문을 서명해서 넣습니다.
Redis 세션에도 같은 버전을 저장합니다. 매 요청마다 DB의 현재 역할·권한을 읽고 검증하여
JWT 안의 오래된 역할·권한 목록을 신뢰하지 않습니다.

- 사용자 역할·상태 변경: 사용자 인증 버전 증가, 해당 계정의 기존 Access·Refresh 거부.
- 역할 권한 변경: 역할의 `permissionRevision` 증가, 그 역할을 부여받은 모든 계정의
  기존 Access·Refresh 거부. 권한을 원래대로 복원해도 이전 토큰은 되살아나지 않습니다.
- 역할 이름만 변경하거나 동일 권한·역할을 다시 저장하면 로그인은 유지합니다.
- 권한이 부족한 유효 세션은 403, 만료·폐기·권한 변경으로 오래된 세션은 401입니다.
- 무효 세션의 Redis 키는 기존 TTL까지 남을 수 있지만 인증에는 사용할 수 없습니다.
  권한 변경 직전에 이미 검증을 마친 진행 중 요청까지 취소하는 구조는 아닙니다.

본인 비활성화·역할 변경 및 본인에게 부여된 역할의 권한 변경을 거부합니다.
ADMIN·USER 시스템 역할을 보호하고 마지막 활성 ADMIN의 해제·비활성화를 차단합니다.
역할·사용자 변경 트랜잭션은 공통 DB 잠금을 사용해 역할 삭제/부여와 마지막 관리자
검사의 경합을 직렬화합니다. Record DTO와 제한적 Lombok 사용 원칙은 유지합니다.
