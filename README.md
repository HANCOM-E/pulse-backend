# pulse-backend

실시간 이벤트 피드백 모니터링 API 서버 (Spring Boot 4 · Java 21).

## 사전 요구

- **JDK 21** — Gradle toolchain이 21을 요구합니다.
- Git

## 로컬 설정

로컬은 **인메모리 H2**로 돌아가 공용 DB(Neon)를 건드리지 않습니다. 설정 파일 하나만 만들면 됩니다.

1. 템플릿을 복사합니다.

   ```bash
   # macOS/Linux/Git Bash
   cp application-local.properties.example application-local.properties
   ```

   ```powershell
   # Windows PowerShell
   Copy-Item application-local.properties.example application-local.properties
   ```

2. 복사한 `application-local.properties`로 로컬(H2)은 바로 실행됩니다. 팀과 동일한 시크릿/설정이 필요하면 `jwt.secret` 등 값을 **팀 내부에 공유된 프로퍼티 파일**의 값으로 채우세요. (이 파일은 `.gitignore`되어 커밋되지 않습니다.)

## 실행

```bash
# macOS/Linux/Git Bash
./gradlew bootRun
```

```powershell
# Windows PowerShell
.\gradlew bootRun
```

IntelliJ에서는 `application-local.properties`만 있으면 실행 구성의 Run 버튼으로 바로 뜹니다(별도 환경변수 설정 불필요).

기본 포트는 **8080**입니다.

## 확인

- 헬스체크 — http://localhost:8080/api/v1/health
- Swagger UI — http://localhost:8080/swagger-ui/index.html
- OpenAPI(JSON) — http://localhost:8080/v3/api-docs

## 테스트

```bash
# macOS/Linux/Git Bash
./gradlew test
```

```powershell
# Windows PowerShell
.\gradlew test
```

## 기술 스택 · 문서

- Spring Boot 4.1 · Java 21 · Spring Data JPA · Spring Security (JWT HttpOnly 쿠키 인증)
- API 계약: [`docs/openapi.yaml`](docs/openapi.yaml) · 팀 Notion(API 명세서·ERD)
- 배포(Render) 환경변수(`SPRING_DATASOURCE_*`·`JWT_SECRET`·`CORS_ALLOWED_ORIGINS`·`AUTH_COOKIE_*`)는 배포 문서를 참고하세요.
