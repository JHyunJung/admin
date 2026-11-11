# Lessons

## Java → Kotlin 전환 (2026-11-06)
- Kotlin `trim()`/`isBlank()` 는 유니코드 공백까지 본다. Java 와 같은 결과가 필요하면 `common/JavaStrings.kt` 의 `javaTrim()`/`javaIsBlank()`.
- mockito-kotlin `any()` 는 null 을 매칭하지 않는다 → `anyOrNull()`. `ArgumentCaptor.capture()` 는 null 을 돌려 Kotlin 파라미터 검사에 걸리고, 남은 matcher 가 다음 테스트의 컨텍스트 로딩까지 깨뜨린다 → `argumentCaptor<T>()`.
- Kotlin `protected` 에는 패키지 접근이 없다.
- `Long::class.java` 는 primitive long. JDBC 결과 타입은 `javaObjectType`.
- 애너테이션 안에서는 `trimIndent()` 를 못 쓴다. 네이티브 `@Query` 는 원문과 같도록 상수 연결로 쓴다(SQL_ID).
- Java 텍스트 블록은 닫는 `"""` 줄까지 들여쓰기 계산에 넣고 끝에 `\n` 을 붙인다. Kotlin `trimIndent()` 는 둘 다 다르다 → `+ "\n"`.
- companion `@JvmStatic main` 은 기동 로그 이름이 `.Companion` 이 된다 → `setMainApplicationClass`.
- 병렬 worktree 에이전트는 main 에서 시작될 수 있다. 시작 커밋을 프롬프트에 명시하고 `git merge --ff-only <branch>` 부터 시키기.
- 변환 그룹끼리는 nullability 가 어긋난다(non-null `Long` vs `Long?`). 병합 직후 전체 컴파일로 잡는다.

## 스냅숏 트리로 커밋할 때 인덱스 (2026-11-10)
- 실수: `git commit-tree` 로 만든 커밋으로 `git reset --soft` 한 뒤 `git add <docs> && git commit` 을 했더니, 인덱스가 옛 HEAD 상태라 docs 커밋이 src 변경을 되돌렸다.
- 규칙: commit-tree 로 브랜치를 옮길 때는 `--soft` 가 아니라 `git reset -q <commit>`(mixed)로 인덱스까지 맞춘다. 다음 커밋 전에 `git diff --cached --stat` 이 의도한 파일만 보이는지 확인한다.

## 동기 작업을 CompletableFuture 합성으로 옮길 때 (2026-11-11)
- 실수: DNS 검사를 `supplyAsync → thenCompose(sendAsync)` 로 옮기자 ① 타임아웃 취소가 안쪽 HTTP future 로 전파되지 않았고 ② 실행기가 무제한이었고 ③ 포기한 대기 작업이 나중에 실행됐다. Codex 2~3라운드에서야 잡혔다.
- 규칙: 합성 future 의 `cancel` 은 안쪽으로 전파되지 않는다 — 안쪽 future 를 따로 들고 함께 취소한다. 블로킹 작업용 실행기는 스레드·대기열 상한과 거절 처리를 같이 정한다. 대기 작업은 실행 직전에 포기 여부를 본다. 이 세 가지를 테스트(연결 종료·대기 작업 미실행)로 고정한 뒤에 검토를 맡긴다.
