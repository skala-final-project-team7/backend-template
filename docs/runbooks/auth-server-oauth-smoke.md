# auth-server OAuth 수동 스모크 테스트 러닝북

> 실제 Atlassian 3LO 를 상대로 login → callback → refresh → logout 전 사이클을 검증한다.
> 최초 작성: 2026-06-12 (첫 스모크 완주 — `read:me` scope 누락·토큰 컬럼 truncation 2건 발견·수정).
> 소요: 준비물 갖춰진 상태에서 약 15분. 자동 테스트(`./scripts/verify.sh`)와 별개로, **모킹이 못 잡는 외부 계약·실 DB 간극**을 잡는 것이 목적이다.

---

## 0. 준비물 (1회성)

- **Atlassian dev console 3LO 앱** (https://developer.atlassian.com/console/myapps/)
  - Permissions 탭 — **Confluence API**: classic 5종
    `read:confluence-content.all`, `readonly:content.attachment:confluence`(granular 처럼 생겼지만 classic), `read:confluence-user`, `read:confluence-groups`, `read:confluence-space.summary`
  - Permissions 탭 — **User Identity API**: `read:me` (⚠️ 누락 시 callback 의 `/me` 가 403 → 서버는 401 응답)
  - Authorization 탭 — Callback URL: `http://localhost:3000/auth/callback`
  - Settings 탭 — Client ID / Secret 확보 (⚠️ secret 은 채팅·문서에 붙여넣지 말 것, 노출 시 rotate)
- Docker (MySQL 8 용), `jq`, `openssl`

## 1. 로컬 인프라

```bash
# MySQL — 마이그레이션(V00x)이 변경된 적 있으면 기존 컨테이너는 체크섬 불일치 → rm -f 후 재생성
docker rm -f lina-mysql 2>/dev/null
docker run -d --name lina-mysql -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=lina_auth -p 3306:3306 mysql:8
docker logs -f lina-mysql 2>&1 | grep -m2 "ready for connections"   # 2줄 뜨면 자동 종료

# JWT 서명 키쌍 (없으면)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-private.pem
openssl rsa -in /tmp/jwt-private.pem -pubout -out /tmp/jwt-public.pem
```

## 2. 서버 기동

⚠️ **여러 줄 `VAR=x \` 접두사 방식은 복붙에서 잘 깨진다 — 한 줄씩 export 로 할 것.**
export 는 그 터미널에서만 유효하므로 **기동까지 같은 터미널**에서 한다.

```bash
cd backend

export MYSQL_USERNAME=root
export MYSQL_PASSWORD=root
export LINA_TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
export LINA_JWT_PRIVATE_KEY="$(cat /tmp/jwt-private.pem)"
export LINA_JWT_PUBLIC_KEY="$(cat /tmp/jwt-public.pem)"
export CONFLUENCE_CLIENT_ID='<Client ID>'
export CONFLUENCE_CLIENT_SECRET='<Secret>'          # <> 괄호 빼고 실제 값. < 는 zsh 리다이렉션 문자
export CONFLUENCE_AUTHORIZATION_URI=https://auth.atlassian.com/authorize
export CONFLUENCE_TOKEN_URI=https://auth.atlassian.com/oauth/token
export CONFLUENCE_USER_INFO_URI=https://api.atlassian.com/me
export CONFLUENCE_REDIRECT_URI=http://localhost:3000/auth/callback
export CONFLUENCE_SCOPES="read:me read:confluence-content.all readonly:content.attachment:confluence read:confluence-user read:confluence-groups read:confluence-space.summary offline_access"

# 빠짐없이 들어갔는지 — 빈 칸([])이 하나라도 있으면 안 됨
echo "AUTH=[$CONFLUENCE_AUTHORIZATION_URI] TOKEN=[$CONFLUENCE_TOKEN_URI] ME=[$CONFLUENCE_USER_INFO_URI]"
echo "REDIR=[$CONFLUENCE_REDIRECT_URI] ID=[$CONFLUENCE_CLIENT_ID] SECRET=[${CONFLUENCE_CLIENT_SECRET:+set}]"
echo "SCOPES=[$CONFLUENCE_SCOPES]"

./gradlew :auth-server:bootRun
```

**✅ 체크포인트 1**: 부팅 로그에 Flyway `V001`~`V004` 적용(또는 `up to date`) + 에러 없이 `Started AuthApplication`.
(`ddl-auto: validate` 가 Entity↔실 스키마 drift 를 여기서 잡는다.)

기동 직후 다른 터미널에서 리다이렉트 구성 확인:
```bash
curl -s -D - -o /dev/null "http://localhost:8081/api/auth/login" | grep -i location
# Location 이 https://auth.atlassian.com/authorize?... 절대 URL + scope/redirect_uri 채워져 있어야 함
# "?audience=..." 처럼 상대경로면 → env 누락 상태로 기동된 것 (아래 트러블슈팅)
```

## 3. 로그인 (브라우저 + code 수집)

1. 브라우저 → `http://localhost:8081/api/auth/login` → Atlassian 로그인 → 동의(scope 목록 확인) → **허용**
2. `http://localhost:3000/auth/callback?state=...&code=...` 으로 이동하며 "연결할 수 없음" — **정상** (FE 없음)
3. **주소창 URL 전체** 복사 — code/state 를 손으로 나누지 말 것

## 4. callback (⏱️ code 유효 5분, state 유효 10분·1회용)

```bash
URL='주소창_전체_붙여넣기'
curl -s "http://localhost:8081/api/auth/callback?${URL#*\?}" | jq
```

**✅ 체크포인트 2**: `200` + `data: { accessToken, refreshToken, expiresAt(+09:00) }`.
JWT claim 확인은 외부 사이트 말고 로컬에서: `echo '<payload 구간>' | base64 -d | jq`
→ `userId`(accountId), `groups`(groupId 배열), `role`, `iss` 확인.

## 5. refresh — 회전 + 재사용 거부

```bash
REFRESH1='<4의 refreshToken>'

# ① 200 + 새 토큰 쌍
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\": \"$REFRESH1\"}" | jq

# ② 같은 옛 토큰 재사용 → 401 이어야 함 ★ Rotating 핵심
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\": \"$REFRESH1\"}" | jq
```

## 6. logout — 무효화

```bash
ACCESS2='<5-①의 새 accessToken>'
REFRESH2='<5-①의 새 refreshToken>'

# ① 200, data: null
curl -s -X POST http://localhost:8081/api/auth/logout -H "Authorization: Bearer $ACCESS2" | jq

# ② logout 후 최신 refresh → 401 이어야 함
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\": \"$REFRESH2\"}" | jq
```

## 7. 실패 경로

```bash
curl -s "http://localhost:8081/api/auth/callback?code=x&state=forged" | jq   # 400 INVALID_REQUEST
curl -s -X POST http://localhost:8081/api/auth/logout | jq                    # 401 (Bearer 없음)
curl -s "http://localhost:8081/internal/auth/foo" | jq                        # 401 (외부 차단)
# admin 게이트: /api/auth/login?mode=admin → 일반 계정이면 callback 403 "관리자 권한이 없는 계정입니다"
```

## 8. DB — 암호화 저장 확인

```bash
docker exec lina-mysql mysql -uroot -proot lina_auth -e \
  "SELECT user_id, email, role FROM users;
   SELECT cloud_id, HEX(LEFT(confluence_access_token_enc,16)) AS enc_head FROM user_tokens;
   SELECT COUNT(*) AS group_cnt FROM user_groups;"
# 토큰 컬럼이 HEX 바이너리(평문 아님), groups 적재, users 1행 확인
```

## 기대 결과 요약

| 단계 | 기대 |
|---|---|
| 2 부팅 | Flyway 적용 + `Started AuthApplication` |
| 4 callback | `200` + LINA 토큰 3종 (Confluence 토큰은 응답에 없음) |
| 5-① / 5-② | `200` 새 쌍 / `401` |
| 6-① / 6-② | `200` `data:null` / `401` |
| 7 | `400` / `401` / `401` / `403` |
| 8 | 암호문 저장·groups 적재 |

## 트러블슈팅 (전부 1차 스모크 실제 발생 사례)

| 증상 | 원인 | 해결 |
|---|---|---|
| 부팅 실패 "JWT 서명 키가 설정되지 않았습니다" | env 미주입 (fail-fast 정상 동작) | §2 export 후 **같은 터미널에서** 재기동 |
| 부팅 실패 "Port 8081 already in use" | 이전 기동 잔여 프로세스 | `lsof -i :8081` 로 **정체 확인 후** kill (무엇인지 모르고 `kill -9` 금지 — Docker 백엔드를 죽인 사례 있음) |
| `Cannot connect to the Docker daemon` | Docker Desktop 죽음 | `open -a Docker` → `docker info` 확인 → `docker start lina-mysql` |
| 부팅 실패 Flyway checksum mismatch | V00x 파일이 변경됨 (미배포 단계 직접 수정 정책) | 컨테이너 `rm -f` 후 재생성 (§1) |
| 브라우저 `ERR_TOO_MANY_REDIRECTS` | `CONFLUENCE_AUTHORIZATION_URI` 등 빈 값 → 상대경로 302 루프 | §2 의 echo 점검 → 누락 export → **재기동** (env 는 기동 시점에 읽힘) |
| `localhost:3000` 연결 거부 | FE 없음 — **정상** | 주소창에서 URL 만 복사해 §4 진행 |
| callback `401` "Confluence 인증에 실패했습니다" | code 만료(5분)/재사용, 또는 Atlassian 호출 실패 | 새 로그인 사이클. 반복되면 격리: code 를 직접 `POST /oauth/token` 교환 → `accessible-resources` → `/me` 순서로 curl (1차 때 이 방법으로 `/me` 403 = `read:me` 누락을 특정) |
| callback `500` `Duplicate entry ... for key 'users.uk_users_email'` | admin seed 의 `LINA_ADMIN_ACCOUNT_ID` 가 Atlassian realm 접두사 `712020:` 빠진 값 → `/me` accountId(`712020:...`)와 `users.user_id` 불일치 → upsert `findByUserId` miss → INSERT 분기가 `email` unique 충돌(2026-06-16 스모크 실측). **본인 계정으로 로그인해도** `404` 아닌 `500`. 해결: `admin.env` accountId 에 `712020:` 추가 + 기존 행 `UPDATE users SET user_id='712020:...' WHERE email='...'`(user_key 불변 → credential FK 유지). 근본 방어(seed accountId 형식 검증)는 별도 코드 작업 | 데이터 교정 후 재로그인 |
| callback `500` (그 외) | 예상 밖 예외 — bootRun 로그에 스택트레이스 있음 | `Unhandled exception` 블록 확인 (1차 때 토큰 컬럼 truncation 발견 경로) |
| curl 이 HTML 400 / 빈 출력 | 경로에 `/api` 누락, `\| jq` 오타, `:3000` 으로 호출 | §4 의 URL-변수 방식 사용 (포트 8081 / `/api/auth/callback` 고정) |

## 10. Feature 5 — 내부 credential 조회 스모크 (admin 로그인 후)

> Data Ingestion Worker 전용 내부 API(`GET /internal/auth/admin-confluence-credential`)의 라이브 검증.
> 첫 통과: 2026-06-16. 호출은 **curl(내부 호출자 stand-in)** — 실 Python Worker end-to-end 는 4단계.

**전제**
- `user_tokens` 는 **로그인 산물**이라 seed 불가 → **admin(`LINA_ADMIN_ACCOUNT_ID` 계정)이 §3~§4 OAuth 로그인을 1회 완주**해 본인 행에 `user_tokens` 가 생겨 있어야 `200`. (다른 계정으로 로그인하면 admin 행엔 토큰이 없어 `404`)
- ⚠️ seed accountId 는 **realm 접두사 `712020:` 포함 전체값**이어야 함 — 빠지면 콜백이 `500`(트러블슈팅 표 참조).
- `export INTERNAL_API_KEY=...` 가 §2 와 같은 터미널에 주입돼 있어야 함(서버는 `${INTERNAL_API_KEY}` 로 검증).

```bash
source /tmp/lina-smoke/admin.env      # LINA_ADMIN_ACCOUNT_ID 로드
KEY="$INTERNAL_API_KEY"; ADMIN="$LINA_ADMIN_ACCOUNT_ID"

# ① 정상 — 200. ⚠️ 응답에 평문 access token 이 들어오므로 값은 마스킹해서 본다(원문 로그/대화 금지)
curl -s -G "http://localhost:8081/internal/auth/admin-confluence-credential" \
  -H "X-Internal-Api-Key: $KEY" --data-urlencode "adminUserId=$ADMIN" \
  | jq '{accessToken_len:(.accessToken|length), cloudId, siteUrl, expiresAt, refreshToken_present:(has("refreshToken"))}'

# ② 키 없음 → 401 (fail-closed)
curl -s -o /dev/null -w "%{http_code}\n" -G "http://localhost:8081/internal/auth/admin-confluence-credential" --data-urlencode "adminUserId=$ADMIN"
# ③ 키 틀림 → 401
curl -s -o /dev/null -w "%{http_code}\n" -G "http://localhost:8081/internal/auth/admin-confluence-credential" -H "X-Internal-Api-Key: wrong" --data-urlencode "adminUserId=$ADMIN"
# ④ adminUserId 누락 → 400 (@NotBlank)
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/internal/auth/admin-confluence-credential" -H "X-Internal-Api-Key: $KEY"
# ⑤ 일반 USER accountId → 403
curl -s -o /dev/null -w "%{http_code}\n" -G "http://localhost:8081/internal/auth/admin-confluence-credential" -H "X-Internal-Api-Key: $KEY" --data-urlencode "adminUserId=<USER 계정 accountId>"
# ⑥ 존재하지 않는 user → 404
curl -s -o /dev/null -w "%{http_code}\n" -G "http://localhost:8081/internal/auth/admin-confluence-credential" -H "X-Internal-Api-Key: $KEY" --data-urlencode "adminUserId=no-such-user"
```

**✅ 체크포인트 (2026-06-16 실측)**

| # | 경로 | 기대 |
|---|---|---|
| ① | 정상 | `200` `{accessToken, cloudId, siteUrl, expiresAt}` — **`refreshToken` 필드 없음**(구조적 차단). `cloudId`=`user_tokens.cloud_id`, `siteUrl`=`admin_atlassian_credential.site_url`, `expiresAt` KST `+09:00` |
| ② / ③ | 키 없음 / 위조 | `401` (fail-closed) |
| ④ | adminUserId 누락 | `400` |
| ⑤ | 일반 USER | `403` "관리자 권한이 없는 계정입니다" |
| ⑥ | 없는 user | `404` "사용자를 찾을 수 없습니다" |

> Feature 6(admin-key activate/deactivate) 라이브 스모크는 `working-log.md` 2026-06-16 참조(실 Premium 사이트 activate `200`→deactivate `200` 한 사이클 통과).

## 9. 뒷정리

- bootRun `Ctrl+C`, `docker stop lina-mysql` (보존) 또는 `docker rm -f lina-mysql`
- secret 이 어딘가 노출됐다면 dev console 에서 rotate
- 발견 사항은 `docs/ai/working-log.md` 에 기록, plan 체크박스는 **실측 근거가 있는 항목만** 갱신

## 한계 (이 스모크가 검증하지 않는 것)

- `GET /api/users/me` — BFF 측 작업 (auth-server plan §교차 모듈 참조) 완료 후 전체 사이클 재확인
- `/internal/*` credential API(Feature 5) — §10 으로 확장(2026-06-16 통과). admin-key API(Feature 6) 라이브 스모크는 `working-log.md` 2026-06-16. 단 둘 다 **curl stand-in** — 실 Python Worker/BFF consumer 경유 end-to-end 는 4단계
- `LINA_TOKEN_ENCRYPTION_KEY` 를 매번 새로 생성하므로 서버 재시작 간 기존 암호화 토큰 복호화 불가 — 스모크는 1세션 내 완료 전제
