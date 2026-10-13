#!/usr/bin/env bash
# FIDO Admin 기동 스크립트.
#
#   scripts/fido-admin.sh start|stop|restart|status
#
# 설정은 같은 디렉터리의 fido-admin.env(없으면 FIDO_ADMIN_ENV 가 가리키는 파일)에서 읽는다.
# 예시는 fido-admin.env.example 에 있다. 이미 환경에 있는 값이 우선한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${FIDO_ADMIN_ENV:-${SCRIPT_DIR}/fido-admin.env}"
if [[ -f "${ENV_FILE}" ]]; then
  # 환경에 이미 있는 값은 덮어쓰지 않는다.
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" == \#* ]] && continue
    key="${key// /}"
    [[ -n "${!key:-}" ]] && continue
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    export "${key}=${value}"
  done < "${ENV_FILE}"
fi

PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1g}"
LOG_PATH="${LOG_PATH:-${APP_HOME}/logs}"
PID_FILE="${PID_FILE:-${APP_HOME}/fido-admin.pid}"
CONSOLE_LOG="${LOG_PATH}/console.out"
# 종료 대기 시간(초). 감사 로그 대기열을 비우는 데 최대 30초를 쓴다.
STOP_TIMEOUT="${STOP_TIMEOUT:-60}"
export LOG_PATH

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
  rm -f "${PID_FILE}"
  return 1
}

start() {
  local pid
  if pid="$(running_pid)"; then
    echo "이미 실행 중입니다 (PID ${pid})."
    return 0
  fi

  local jar
  jar="$(find_jar)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    echo "실행할 jar 를 찾지 못했습니다. ./gradlew bootJar 로 빌드하거나 APP_JAR 를 지정하세요." >&2
    return 1
  fi

  if [[ "${PROFILE}" != "local" ]]; then
    local missing=()
    for v in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
      [[ -n "${!v:-}" ]] || missing+=("${v}")
    done
    if (( ${#missing[@]} > 0 )); then
      echo "환경 변수가 없습니다: ${missing[*]} (${ENV_FILE} 또는 환경에 지정)" >&2
      return 1
    fi
  fi

  mkdir -p "${LOG_PATH}"
  echo "시작: ${jar} (profile=${PROFILE})"
  # shellcheck disable=SC2086
  nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${jar}" --spring.profiles.active="${PROFILE}" \
    >> "${CONSOLE_LOG}" 2>&1 &
  echo $! > "${PID_FILE}"

  sleep 3
  if pid="$(running_pid)"; then
    echo "실행 중 (PID ${pid}). 콘솔 출력: ${CONSOLE_LOG}"
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
  echo "종료 중 (PID ${pid}) ..."
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
  rm -f "${PID_FILE}"
  echo "종료했습니다."
}

status() {
  local pid
  if pid="$(running_pid)"; then
    echo "실행 중 (PID ${pid}, profile=${PROFILE})"
  else
    echo "실행 중이 아닙니다."
    return 3
  fi
}

case "${1:-}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  *)
    echo "사용법: $0 {start|stop|restart|status}" >&2
    exit 2
    ;;
esac
