# 프로젝트 규칙 (CLAUDE.md) — 백엔드 레포

> 이 파일은 매 세션 자동 로드되는 AI 가이드입니다.
> **강제되는 규칙(lint/hook)은 여기 중복 기재하지 않습니다.** 아래는 툴로 못 잡는 규칙 + 문서 링크만 다룹니다.
> 진실의 원천: 커밋 컨벤션은 commitlint(도입 시)가 강제합니다. 백엔드 코드 스타일 강제 도구(Checkstyle/Spotless 등)는 아직 팀이 정하지 않았으므로, 정해지기 전까지는 이 문서의 규칙이 임시 진실의 원천입니다.
> 프론트엔드는 별도 레포(`CLAUDE.frontend.md` 참고)에서 관리합니다. 이 문서는 백엔드 레포에만 적용됩니다.

## ⚠ 자동 세팅 전 필수 확인

아래 항목은 이 문서만으로 자동 결정할 수 없습니다. "CLAUDE.md 기준으로 세팅해줘"를 실행하다가 이 지점에서 멈추고 팀에 확인해야 합니다. 나머지 항목(Java 버전, `.editorconfig`, `.gitattributes` 등)은 구체적인 값이 있어 바로 실행 가능합니다.

- **백엔드 호스팅 서비스** — Render를 제안했지만, 무료 티어에서 부하가 걸리면 메모리 부족(OOM)으로 크래시한 사례가 검색 결과로 확인되어 발표 당일 리스크가 있습니다. 2026-07-29 팀 회의에서 Render와 AWS 프리티어의 트레이드오프를 확인한 뒤 결정하기로 했으므로, 비교 결과가 나오기 전까지는 확정값이 아닙니다.

## 프로젝트 개요

**Pulse** — 실시간 이벤트 피드백 모니터링 서비스입니다. 참가자 피드백을 실시간 대시보드·백오피스·SSR 공개 페이지로 보여줍니다.

## 확정된 기술 스택 (백엔드)

| 레이어 | 선택 | 비고 |
|---|---|---|
| 백엔드 | Spring Boot 4.1.0 (Gradle, Groovy DSL) | 담당 1명, 의도적으로 가볍게 설계. 빌드 도구는 원 기획서가 "Gradle/빈 설정"을 학습 난점으로 명시해 Gradle을 전제합니다. Groovy DSL은 2026-07-29 팀 확인. |
| DB | PostgreSQL 무료 티어 (Neon 또는 Supabase) | 원 기획서 아키텍처 다이어그램에 명시돼 있습니다. |
| 인증 | Spring Security + 이메일/비번 + JWT(또는 세션) + 단일 역할 | 원 기획서 "최소 실물 auth만" 절에 명시된 범위입니다. |
| 실시간 (서버) | 폴링 → SSE 승급 | 초기에는 폴링 응답 API만 제공하고, 이후 SSE 엔드포인트를 추가합니다. 프론트와 전환 시점을 맞춰야 합니다. |
| LLM | 무료 티어 API(Gemini 등) | 종료 후 배치 요약 1회만 호출. (누가 호출할지는 원 기획서에 명시가 없어 DB 접근이 필요한 백엔드 배치 잡으로 가정했습니다. 프론트 쪽 호출이 맞다면 이 항목을 옮겨야 합니다.) |
| 배포 | 무료 백엔드 호스팅 | 비용 0 유지가 원칙. 서비스명은 위 "⚠ 자동 세팅 전 필수 확인" 참고 |

## 개발 환경 설정

| 항목 | 값 | 근거 |
|---|---|---|
| Java 버전 | Java 21 (LTS) | Spring Boot 3는 Java 17 이상이 최소 요구 사항이며, 2026년 기준 신규 프로젝트에는 더 최신 LTS인 Java 21이 권장됩니다. ([goregulus.com](https://goregulus.com/cra-basics/spring-boot-versions/), [technetexperts.com](https://www.technetexperts.com/java-25-vs-21-postgresql-risk/)) |
| 빌드 도구 | Gradle (Groovy DSL) | "확정된 기술 스택" 표 참고. 원 기획서가 Gradle을 전제하며, DSL(Groovy vs Kotlin)은 2026-07-29 팀 확인으로 Groovy를 선택했습니다. |
| Spring Boot 버전 | 4.1.0 | Spring Initializr 기본 추천값을 그대로 사용했습니다(2026-07-29 스캐폴딩 시점 기준). |
| groupId / artifactId | `com.hancome.pulse` / `pulse-backend` | 2026-07-29 팀 확인. |
| 코드 스타일/린트 도구 | **미정** | Checkstyle, Spotless 등 백엔드 코드 스타일 강제 도구를 팀이 아직 정한 적이 없습니다. 백엔드 담당자가 정해야 합니다. |
| 패키지/레이어 구조 | **미정** | controller/service/repository 레이어 분리 여부 등 프로젝트 구조를 팀이 아직 정한 적이 없습니다. 백엔드 담당자가 정해야 합니다. |

### Claude 제안 항목 — 웹 검색 기반으로 Claude가 채움

> **주의:** 아래 항목은 팀원 초안에 없던 내용입니다. Claude가 웹 검색으로 확인한 일반적인 관례를 근거로 제안한 값이므로, 팀 초안과 동일한 무게로 받아들이면 안 됩니다. 회의에서 **채택/수정/거부**를 명시적으로 확정해야 합니다.

| 항목 | Claude 제안값 | 근거 |
|---|---|---|
| 백엔드 호스팅 서비스 | **Render (무료 티어)** — ⚠ 자동 세팅 중단, 팀 검증 필요 | 2026년 기준 Render/Railway/Fly.io 비교에서 Spring Boot를 실제로 무료로 돌릴 수 있는 곳은 Render뿐입니다. 다만 15분 비활성 시 슬립, 콜드스타트 30초 이상, 부하 시 메모리 부족(OOM) 크래시 사례가 확인되어 발표 당일 리스크로 남습니다. 이는 원 기획서가 이미 "무료 티어는 콜드스타트·잠자기가 붙는다"고 예상한 리스크와 일치합니다. ([bswen.com](https://docs.bswen.com/blog/2026-02-28-springboot-free-hosting/), [render.com](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026)) 2026-07-29 팀 회의에서는 이 제안을 그대로 채택하지 않고, Render와 AWS 프리티어의 트레이드오프를 팀이 직접 확인한 뒤 결정하기로 했습니다. |
| `.editorconfig` (에디터 호환) | 도입 + 아래 내용 | WebStorm은 EditorConfig 지원이 기본 활성화, IntelliJ IDEA도 내장 지원됩니다. VS Code만 "EditorConfig for VS Code" 확장 프로그램을 팀원이 직접 설치해야 합니다. ([JetBrains WebStorm 문서](https://www.jetbrains.com/help/webstorm/editorconfig.html), [JetBrains IntelliJ 문서](https://www.jetbrains.com/help/idea/editorconfig.html), [VS Code 확장](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig)) |
| `.gitattributes` ✅ 적용됨 (2026-07-29) | 도입 + 아래 내용 | `* text=auto`로 개행을 자동 정규화하고, 소스 코드는 `eol=lf`로 통일하며 Windows 전용 스크립트만 `eol=crlf`로 강제하는 방식이 일반적 관례입니다. 프론트엔드와 동일한 원칙(`*.java`, `*.gradle`, `*.properties`, `*.yml`)에 Spring Initializr가 자체 생성한 `/gradlew`, `*.jar binary` 규칙을 합쳐서 실제 파일로 적용했습니다. ([rehansaeed.com](https://rehansaeed.com/gitattributes-best-practices/), [dev.to](https://dev.to/ramunarasinga-11/-textauto-in-gitattributes-file-4ba5)) |
| `.gitignore` 세부 항목 ✅ 적용됨 (2026-07-29, 제안값 변경) | Spring Initializr 기본값 사용 | 스캐폴딩 시 GitHub 공식 Gradle 템플릿 대신 Spring Initializr가 실제로 생성한 `.gitignore`를 채택했습니다. `.gradle`, `build/`, IDE별(STS/IntelliJ/NetBeans/VS Code) 산출물, `HELP.md`를 포함합니다. 시크릿 파일(`application-local.yml`, `.env` 등) 제외는 아직 별도로 추가하지 않았으니 실제 시크릿 파일이 생기기 전에 추가해야 합니다. |
| 환경 변수/시크릿 관리 방식 | 시크릿이 포함된 설정(`application-local.yml` 또는 `.env`)은 커밋하지 않고, 템플릿(`application-example.yml` 또는 `.env.example`)만 커밋합니다 | Spring Boot 프로젝트의 일반적인 관례입니다. 프론트엔드의 `.env.example`/`.env.local` 원칙과 동일한 취지를 백엔드 설정 파일명에 맞게 적용했습니다. |

```gitattributes
# .gitattributes (Claude 제안 + Spring Initializr 기본값 병합, 2026-07-29 실제 적용)
* text=auto
*.java text eol=lf
*.gradle text eol=lf
*.properties text eol=lf
*.yml text eol=lf
*.yaml text eol=lf
*.md text eol=lf
*.sh text eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf

# Spring Initializr 기본값
/gradlew text eol=lf
*.jar binary
```

```ini
# .editorconfig (Claude 제안, VS Code는 "EditorConfig for VS Code" 확장 설치 필요)
root = true

[*]
charset = utf-8
indent_style = space
indent_size = 2
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.md]
trim_trailing_whitespace = false
```

```gitignore
# .gitignore (Spring Initializr 기본값, 2026-07-29 실제 적용)
HELP.md
.gradle
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/main/**/build/
!**/src/test/**/build/

### STS ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache
bin/
!**/src/main/**/bin/
!**/src/test/**/bin/

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr
out/
!**/src/main/**/out/
!**/src/test/**/out/

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/

### VS Code ###
.vscode/

# 시크릿 (Claude 제안 — 아직 반영 안 됨, 실제 시크릿 파일 생기기 전에 추가 필요)
application-local.yml
application-secret.yml
.env
```

## 명령어

- 테스트: `./gradlew test`
- 빌드: `./gradlew build`
- 실행: `./gradlew bootRun`

---

## 코드 규칙 (린트로 못 잡는 것만 — 반드시 준수)

> 아직 팀이 백엔드 전용 코드 규칙(네이밍, 레이어 구조, 예외 처리 방식 등)을 정하지 않았습니다. 정해지는 대로 이 섹션을 채워야 합니다.

## 프론트엔드 연동 원칙

- 계약 우선 개발이 원칙입니다. 프론트엔드의 MSW 목 스키마가 단일 소스이며, 실제 Spring 응답은 이 스키마를 따라야 합니다.
- 백엔드 담당이 1명뿐이라 크리티컬 패스가 좁습니다. API 스펙을 변경할 때는 반드시 프론트 담당자와 먼저 협의해야 합니다.
- 프론트엔드는 별도 레포에서 관리되므로, API 계약 변경은 두 레포 모두에 반영돼야 합니다.

---

## Git 규칙

- **main/dev 직접 커밋·push는 금지합니다.** 항상 `feature/…`·`fix/…` 브랜치에서 작업 후 PR을 올려야 합니다.
- 커밋 메시지는 [Conventional Commits](https://www.conventionalcommits.org/) 형식을 따라야 합니다.

  ```
  <type>(<scope>): <subject>

  <body>

  <footer>
  ```

  - **type**: 아래 표를 따르며 소문자로 작성해야 합니다.
  - **scope**: 변경 범위(예: `auth`, `dashboard`, `feedback`)이며 선택 사항입니다.
  - **subject**: 50자 이내, 명령문(imperative mood)으로 작성하고 마침표는 붙이지 않습니다.
  - **body**: 무엇을 왜 바꿨는지, 그리고 세션 중 겪은 애로사항·트러블슈팅을 기재해야 합니다.
  - **footer**: `BREAKING CHANGE:` 또는 이슈 참조(`Refs #123`)를 기재합니다.

  | type | 의미 |
  |---|---|
  | `feat` | 새로운 기능 추가 |
  | `fix` | 버그 수정 |
  | `docs` | 문서 변경 (코드 변경 없음) |
  | `style` | 포맷팅 등 코드 동작에 영향 없는 변경 |
  | `refactor` | 기능 변경 없는 코드 구조 개선 |
  | `perf` | 성능 개선 |
  | `test` | 테스트 추가/수정 |
  | `build` | 빌드 시스템, 의존성 변경 |
  | `ci` | CI 설정 변경 |
  | `chore` | 그 외 잡다한 변경 |
  | `revert` | 이전 커밋 되돌리기 |

  예시:
  ```
  feat(feedback): 감정 분석 배치 요약 API 추가

  이벤트 종료 후 LLM 호출로 Report를 생성하는 배치 잡 구현.

  Refs #12
  ```

- 하나의 커밋은 하나의 논리적 변경만 포함해야 합니다(기능 추가와 리팩토링을 한 커밋에 섞지 않습니다).
- 커밋 전 diff를 확인하고, 의도하지 않은 파일(로그, 임시파일, `application-local.yml` 등)이 포함되지 않았는지 점검해야 합니다.
- 작업 전 `git pull --rebase origin dev`로 최신화해야 합니다.

## PR 규칙

- PR을 열기 전 브랜치가 최신 base 브랜치를 기준으로 하는지 확인해야 합니다.
- `.github/PULL_REQUEST_TEMPLATE.md`가 존재하면 반드시 해당 양식을 채워서 사용해야 합니다.
- PR 제목도 커밋 컨벤션과 동일한 `type: subject` 형식을 따라야 합니다.
- 관련 이슈가 있으면 본문에 `Closes #번호`/`Fixes #번호`/`Refs #번호`로 명시해야 합니다.
- 하나의 PR은 하나의 목적만 다뤄야 합니다. 여러 기능/수정이 섞여 있으면 분리를 제안해야 합니다.
- PR 생성은 `gh pr create`를 사용해야 합니다(웹 UI로 유도하지 않습니다).
- 아래의 경우 PR을 열기 전 사용자에게 먼저 확인해야 합니다.
  - draft로 열지 여부
  - base 브랜치가 `main`/`dev`가 아닌 경우
  - 리뷰어/라벨 지정 여부

## 브랜치 네이밍

```
feature/<설명>
fix/<설명>
hotfix/<설명>
chore/<설명>
```

kebab-case를 사용하며, 이슈 번호가 있으면 `fix/12-sse-connection-leak`처럼 포함해야 합니다.

---

## AI 작업 시 주의

- 커밋·push·PR 생성은 **사용자 승인 후** 진행해야 합니다.
- `.env`/`application-local.yml` 등 시크릿 파일 접근은 금지합니다.
- 요청 범위 밖 코드는 건드리지 않아야 합니다(surgical change).
