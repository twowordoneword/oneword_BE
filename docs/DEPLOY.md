# 운영 배포 (EC2 + RDS)

```
GitHub (main 푸시) ─ CI 통과 ─▶ Deploy 워크플로
                                 ├─ 이미지 빌드 → GHCR (ghcr.io/twowordoneword/oneword_be:<sha>)
                                 └─ SSH로 EC2 접속 → docker compose pull/up → 기동 확인(실패 시 직전 이미지로 롤백)

EC2 ── nginx(80/443, TLS·요청 제한) ──▶ app(127.0.0.1:8080) ──▶ RDS MySQL
```

| 파일 | 역할 |
|---|---|
| `Dockerfile` | JDK 21로 빌드, JRE 이미지에서 비루트 사용자로 실행 |
| `deploy/docker-compose.prod.yml` | 운영 실행 정의 (app + nginx). DB는 RDS라 포함하지 않음 |
| `deploy/nginx/templates/default.conf.template` | HTTPS, 로그인 요청 제한, 관리자 API 외부 차단 |
| `deploy/.env.prod.example` | 서버 `.env` 양식 |
| `deploy/schema-check.sql` | 운영 DB 스키마 드리프트 점검 |
| `.github/workflows/deploy.yml` | 자동 배포 |

---

## 1회 준비

### AWS
1. **RDS (MySQL 8.0)** — 퍼블릭 액세스 **끔**. 보안그룹 인바운드 3306은 **EC2 보안그룹에서만** 허용.
   DB 이름 `musing`, 문자셋 `utf8mb4`. 앱 전용 계정을 만들고 `musing` 스키마 권한만 준다.
2. **EC2 (Ubuntu 22.04+, t3.small 이상 권장)** — 탄력적 IP 연결.
   보안그룹 인바운드: **80·443은 전체**, **22는 내 IP와 GitHub Actions만**(어렵다면 키 인증만 허용하고 비밀번호 로그인 끄기). 8080은 열지 않는다.
3. 도메인 A 레코드를 탄력적 IP로.

### EC2 서버
```bash
# Docker + compose 플러그인
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 재접속 필요

mkdir -p ~/musing/certbot-www && cd ~/musing
# deploy/.env.prod.example 내용을 붙여 넣고 값 채우기
nano .env && chmod 600 .env
```

시크릿 생성:
```bash
openssl rand -base64 48   # APP_JWT_SECRET
```
```bash
openssl rand -hex 32      # ADMIN_TOKEN
```

TLS 인증서 (nginx가 뜨기 전에 한 번, 80 포트가 비어 있어야 함):
```bash
sudo apt-get install -y certbot
sudo certbot certonly --standalone -d <도메인> --agree-tos -m <이메일> --non-interactive
```
standalone 방식은 갱신 때도 80 포트를 비워야 하므로, **첫 배포로 nginx가 뜬 뒤** webroot 방식으로 한 번 다시 받아 둔다.
이 설정이 저장돼 이후엔 certbot 타이머가 nginx를 멈추지 않고 자동 갱신하고 nginx를 다시 읽힌다.
```bash
sudo certbot certonly --webroot -w /home/$USER/musing/certbot-www -d <도메인> --force-renewal \
  --deploy-hook "docker compose -f /home/$USER/musing/docker-compose.prod.yml exec nginx nginx -s reload"
```

### GitHub
**Settings → Environments → `production`** 을 만들고 시크릿 3개를 넣는다. 승인자를 지정하면 배포 전에 승인을 받는다.

| 시크릿 | 값 |
|---|---|
| `EC2_HOST` | 탄력적 IP 또는 도메인 |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | 배포 전용 SSH 개인키 (공개키는 서버 `~/.ssh/authorized_keys`에) |

이미지 pull은 워크플로가 실행 중에만 유효한 `GITHUB_TOKEN`으로 로그인하므로 서버에 GHCR 토큰을 저장할 필요가 없다.

---

## 배포

- **자동** — `main`에 머지 → CI 통과 → Deploy 실행.
- **수동** — Actions → Deploy → Run workflow.

배포 스크립트는 `GET /api/v1/auth/me`가 401을 돌려줄 때까지 최대 90초 기다린다. 응답이 없으면 앱 로그를 출력하고
**직전 이미지(`~/musing/.deployed-image`)로 자동 롤백**한 뒤 워크플로를 실패로 끝낸다.
운영 설정이 규칙을 어기면 `ProductionConfigGuard`가 기동을 막으므로 이 단계에서 드러난다 (README "운영 설정 점검").

### 수동 롤백
```bash
cd ~/musing
APP_IMAGE=ghcr.io/twowordoneword/oneword_be:<되돌릴 커밋 SHA> docker compose -f docker-compose.prod.yml up -d
```

### 관리자 배치 실행
관리자 API는 nginx에서 막혀 있다. 서버에 SSH로 들어가 직접 호출한다.
```bash
curl -X POST http://127.0.0.1:8080/api/v1/admin/batch/chart -H "X-Admin-Token: $(grep ^ADMIN_TOKEN= ~/musing/.env | cut -d= -f2-)"
```

---

## 운영 DB 스키마 드리프트 점검

Flyway baseline으로 넘어온 DB는 V1이 실행된 적이 없어 제약이 빠져 있을 수 있다(로컬에서 `uq_users_provider`가 없던 사례).
**첫 배포 전과 후**에 한 번씩 돌린다.

```bash
mysql -h <rds-endpoint> -u musing -p musing < deploy/schema-check.sql
```

- 첫 번째 결과: 인덱스·외래키 목록. `status`가 전부 `OK`면 통과. 문제 행이 맨 위에 나온다.
  - `MISSING` — 없음 / `MISMATCH` — 컬럼 순서·유니크·참조가 다름 / `UNEXPECTED` — `users.email` 단일 유니크가 남아 있음(V3 미적용)
- 두 번째 결과: `flyway_schema_history`. V1~V3가 `success = 1`이어야 한다.

**새로 만든 RDS**라면 앱이 처음 뜰 때 Flyway가 V1부터 실행하므로 드리프트가 생기지 않는다. 기존 DB를 옮겨 오는 경우에만 특히 주의한다.

---

## 보안 체크리스트
- [ ] RDS 퍼블릭 액세스 꺼짐, 3306은 EC2 보안그룹만
- [ ] EC2 8080 미개방, 22는 제한된 IP
- [ ] `.env` 권한 600, 저장소에 커밋되지 않음
- [ ] `CORS_ALLOWED_ORIGIN_PATTERNS`에 실제 프론트 출처만
- [ ] `DB_URL`에 `sslMode=REQUIRED`
- [ ] HTTPS 인증서 갱신 동작 확인 (`sudo certbot renew --dry-run`)
