#!/usr/bin/env bash
# FIDO Admin 기동 스크립트.
#
#   scripts/fido-admin.sh start   [프로파일]   예) start qa, start prod, start local, start qa,debug
#   scripts/fido-admin.sh restart [프로파일]   프로파일을 빼면 실행 중인 프로파일 그대로 다시 띄운다
#   scripts/fido-admin.sh stop
#   scripts/fido-admin.sh status
#
# 프로파일은 인자 > SPRING_PROFILES_ACTIVE 환경 변수 > fido-admin.env 의 값 > prod 순으로 정한다.
# 설정 파일은 같은 디렉터리에서 읽는다(디렉터리는 FIDO_ADMIN_ENV_DIR 로 바꿀 수 있다).
#   fido-admin-<프로파일>.env  프로파일 전용 값(여러 개면 쉼표로 나눈 각 프로파일 파일을 차례로)
#   fido-admin.env              모든 프로파일 공통 값
# 같은 키는 환경 변수 > 프로파일 전용 파일 > 공통 파일 순으로 앞의 것이 이긴다.
# 예시는 fido-admin.env.example 에 있다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_DIR="${FIDO_ADMIN_ENV_DIR:-${SCRIPT_DIR}}"
COMMON_ENV="${ENV_DIR}/fido-admin.env"

# KEY=VALUE 파일을 읽어 아직 정해지지 않은 변수만 export 한다. 값의 바깥 따옴표는 벗긴다.
load_env() {
  local file="$1" key value
  [[ -f "${file}" ]] || return 0
  while IFS='=' read -r key value || [[ -n "${key}" ]]; do
    key="${key//[[:space:]]/}"
    [[ -z "${key}" || "${key}" == \#* ]] && continue
    [[ -n "${!key:-}" ]] && continue
    value="${value%$'\r'}"
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    export "${key}=${value}"
  done < "${file}"
}

# 공통 파일에 적힌 프로파일 값만 미리 본다(환경에 export 하지 않는다).
profile_in_common_env() {
  [[ -f "${COMMON_ENV}" ]] || return 0
  sed -n 's/^[[:space:]]*SPRING_PROFILES_ACTIVE[[:space:]]*=[[:space:]]*//p' "${COMMON_ENV}" \
    | tail -n 1 | tr -d "\"'\r"
}

COMMAND="${1:-}"
PROFILE_ARG="${2:-}"

PID_FILE="${PID_FILE:-${APP_HOME}/fido-admin.pid}"
PROFILE_FILE="${PID_FILE}.profile"

# restart 에 프로파일을 주지 않으면 지금 실행 중인 프로파일을 이어 쓴다.
if [[ "${COMMAND}" == "restart" && -z "${PROFILE_ARG}" && -f "${PROFILE_FILE}" ]]; then
  PROFILE_ARG="$(cat "${PROFILE_FILE}")"
fi

PROFILE="${PROFILE_ARG:-${SPRING_PROFILES_ACTIVE:-}}"
[[ -n "${PROFILE}" ]] || PROFILE="$(profile_in_common_env)"
PROFILE="${PROFILE:-prod}"
PROFILE="${PROFILE//[[:space:]]/}"
if [[ ! "${PROFILE}" =~ ^[A-Za-z0-9_-]+(,[A-Za-z0-9_-]+)*$ ]]; then
  echo "프로파일 이름이 올바르지 않습니다: '${PROFILE}' (영문·숫자·_·- 를 쉼표로 나열)" >&2
  exit 2
fi
export SPRING_PROFILES_ACTIVE="${PROFILE}"

IFS=',' read -r -a PROFILES <<< "${PROFILE}"
for p in "${PROFILES[@]}"; do
  load_env "${ENV_DIR}/fido-admin-${p}.env"
done
load_env "${FIDO_ADMIN_ENV:-${COMMON_ENV}}"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1g}"
LOG_PATH="${LOG_PATH:-${APP_HOME}/logs}"
CONSOLE_LOG="${LOG_PATH}/console.out"
# 종료 대기 시간(초). 감사 로그 대기열을 비우는 데 최대 30초를 쓴다.
STOP_TIMEOUT="${STOP_TIMEOUT:-60}"
# 앱 로그 파일 위치는 logging.file.path 로 정해진다(logback-spring.xml). LOG_PATH 를 그대로 넘긴다.
export LOGGING_FILE_PATH="${LOGGING_FILE_PATH:-${LOG_PATH}}"

has_profile() {
  local p
  for p in "${PROFILES[@]}"; do [[ "${p}" == "$1" ]] && return 0; done
  return 1
}

find_jar() {
  if [[ -n "${APP_JAR:-}" ]]; then
    echo "${APP_JAR}"
    return
  fi
  # 앱 디렉터리, 빌드 출력 순으로 가장 최근 jar 를 쓴다(-plain.jar 는 실행할 수 없다).
  local dir jar newest
  for dir in "${APP_HOME}" "${APP_HOME}/build/libs"; do
    newest=""
    for jar in "${dir}"/fido-admin*.jar; do
      [[ -f "${jar}" && "${jar}" != *-plain.jar ]] || continue
      if [[ -z "${newest}" || "${jar}" -nt "${newest}" ]]; then newest="${jar}"; fi
    done
    if [[ -n "${newest}" ]]; then
      echo "${newest}"
      return
    fi
  done
}

running_pid() {
  [[ -f "${PID_FILE}" ]] || return 1
  local pid
  pid="$(cat "${PID_FILE}")"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    echo "${pid}"
    return 0
  fi
  rm -f "${PID_FILE}" "${PROFILE_FILE}"
  return 1
}

running_profile() {
  if [[ -f "${PROFILE_FILE}" ]]; then cat "${PROFILE_FILE}"; else echo "알 수 없음"; fi
}

# jar 안에 application-<프로파일>.yml 이 없으면 알려 준다. 기동은 막지 않는다(공통 설정으로 뜬다).
warn_unknown_profiles() {
  local jar="$1" p entries
  command -v unzip >/dev/null 2>&1 || return 0
  entries="$(unzip -Z1 "${jar}" 'BOOT-INF/classes/application-*' 2>/dev/null || true)"
  for p in "${PROFILES[@]}"; do
    if ! grep -qx "BOOT-INF/classes/application-${p}.yml" <<< "${entries}" \
       && ! grep -qx "BOOT-INF/classes/application-${p}.properties" <<< "${entries}"; then
      echo "참고: jar 에 application-${p}.yml 이 없습니다. '${p}' 는 공통 설정(application.yml)과 환경 변수로만 동작합니다." >&2
    fi
  done
}

start() {
  local pid
  if pid="$(running_pid)"; then
    echo "이미 실행 중입니다 (PID ${pid}, profile=$(running_profile))."
    return 0
  fi

  local jar
  jar="$(find_jar)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    echo "실행할 jar 를 찾지 못했습니다. ./gradlew bootJar 로 빌드하거나 APP_JAR 를 지정하세요." >&2
    return 1
  fi
  warn_unknown_profiles "${jar}"

  # local 은 로컬 도커 DB 가 설정에 고정돼 있다. 그 밖의 프로파일은 DB 접속 정보가 반드시 있어야 한다.
  if ! has_profile local; then
    local missing=() v
    for v in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
      [[ -n "${!v:-}" ]] || missing+=("${v}")
    done
    if (( ${#missing[@]} > 0 )); then
      echo "환경 변수가 없습니다: ${missing[*]}" >&2
      echo "  ${ENV_DIR}/fido-admin-${PROFILES[0]}.env 또는 ${COMMON_ENV} 에 적거나 환경에 지정하세요." >&2
      return 1
    fi
  fi

  mkdir -p "${LOG_PATH}"
  echo "시작: ${jar} (profile=${PROFILE})"
  # shellcheck disable=SC2086
  nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${jar}" --spring.profiles.active="${PROFILE}" \
    >> "${CONSOLE_LOG}" 2>&1 &
  echo $! > "${PID_FILE}"
  echo "${PROFILE}" > "${PROFILE_FILE}"

  sleep 3
  if pid="$(running_pid)"; then
    echo "실행 중 (PID ${pid}, profile=${PROFILE}). 콘솔 출력: ${CONSOLE_LOG}"
  else
    echo "기동에 실패했습니다. ${CONSOLE_LOG} 를 확인하세요." >&2
    tail -n 30 "${CONSOLE_LOG}" >&2 || true
    return 1
  fi
}

stop() {
  local pid
  if ! pid="$(running_pid)"; then
    echo "실행 중이 아닙니다."
    return 0
  fi
  echo "종료 중 (PID ${pid}, profile=$(running_profile)) ..."
  kill -TERM "${pid}"
  local waited=0
  while kill -0 "${pid}" 2>/dev/null; do
    if (( waited >= STOP_TIMEOUT )); then
      echo "${STOP_TIMEOUT}초 안에 끝나지 않아 강제 종료합니다." >&2
      kill -KILL "${pid}" 2>/dev/null || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  rm -f "${PID_FILE}" "${PROFILE_FILE}"
  echo "종료했습니다."
}

status() {
  local pid
  if pid="$(running_pid)"; then
    echo "실행 중 (PID ${pid}, profile=$(running_profile))"
  else
    echo "실행 중이 아닙니다."
    return 3
  fi
}

case "${COMMAND}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  *)
    echo "사용법: $0 {start [프로파일]|restart [프로파일]|stop|status}" >&2
    echo "  프로파일 예: prod, qa, local, qa,debug (생략하면 SPRING_PROFILES_ACTIVE 또는 fido-admin.env, 기본 prod)" >&2
    exit 2
    ;;
esac
