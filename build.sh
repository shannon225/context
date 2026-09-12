#!/usr/bin/env bash
#
# Build Context, and set up the tools it needs.
#
#   ./build.sh maven      download Apache Maven into build-cache/
#   ./build.sh pyisopep   create a pyIsoPEP virtualenv under tools/
#	./build.sh javapot 	  build the pinned Scribe prealignment runtime
#   ./build.sh jar        build the self-contained jar
#   ./build.sh sif        build the Apptainer image (needs the jar)
#   ./build.sh            maven + jar + sif
#
# Everything this script downloads or creates lives inside this repository:
#
#   build-cache/apache-maven-<version>/   Maven itself
#   build-cache/m2-repository/            Maven's downloaded dependencies
#   build-cache/apptainer/                Apptainer's image cache
#   tools/pyisopep/                       the pyIsoPEP virtualenv
#
# Delete build-cache/ to reclaim the space.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly CACHE_DIR="${REPO_ROOT}/build-cache"
readonly TOOLS_DIR="${REPO_ROOT}/tools"

readonly MAVEN_VERSION="${CONTEXT_MAVEN_VERSION:-3.9.9}"
readonly MAVEN_HOME="${CACHE_DIR}/apache-maven-${MAVEN_VERSION}"
readonly MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

readonly JAVA_MODULE="${CONTEXT_JAVA_MODULE:-Java/17.0.19-bdist}"
readonly PYTHON_MODULE="${CONTEXT_PYTHON_MODULE:-Python/3.13.5-bare-gcc-2025b-eb}"

readonly PYISOPEP_ENV="${TOOLS_DIR}/pyisopep"
readonly ENCYCLOPEDIA_JAR="libs/maccoss/encyclopedia/6.6.24/encyclopedia-6.6.24.jar"
readonly MSRAWJAVA_JAR="libs/org/searlelab/msrawjava-core-nolice/26.7.31/msrawjava-core-nolice-26.7.31.jar"

export MAVEN_USER_HOME="${CACHE_DIR}/maven-home"
export APPTAINER_CACHEDIR="${CACHE_DIR}/apptainer"
export SINGULARITY_CACHEDIR="${APPTAINER_CACHEDIR}"
export PIP_CACHE_DIR="${CACHE_DIR}/pip"
export XDG_CACHE_HOME="${CACHE_DIR}/xdg"
readonly MAVEN_REPO="${CACHE_DIR}/m2-repository"
readonly MAVEN_REPO="${CONTEXT_MAVEN_REPO:-${CACHE_DIR}/m2-repository}"

log()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

load_module_if_needed() {
    local tool="$1" module_name="$2"
    if command -v "${tool}" > /dev/null 2>&1; then
        return
    fi
    command -v module > /dev/null 2>&1 \
        || fail "No ${tool} on PATH and no 'module' command. Install it, or load it yourself."

    log "Loading ${module_name}"
    module load "${module_name}" \
        || fail "Could not load ${module_name}. Run 'module avail' to find the right name."
}

setup_maven() {
    if command -v mvn > /dev/null 2>&1; then
        log "Using the mvn already on PATH: $(mvn --version 2>/dev/null | head -1)"
        MVN="mvn"
        return
    fi
    if [ -x "${MAVEN_HOME}/bin/mvn" ]; then
        log "Using Maven from ${MAVEN_HOME}"
        MVN="${MAVEN_HOME}/bin/mvn"
        return
    fi

    log "Downloading Apache Maven ${MAVEN_VERSION} into build-cache/"
    mkdir -p "${CACHE_DIR}"
    curl -fsSL -o "${CACHE_DIR}/maven.tar.gz" "${MAVEN_URL}" \
        || fail "Could not download Maven from ${MAVEN_URL}"
    tar xzf "${CACHE_DIR}/maven.tar.gz" -C "${CACHE_DIR}"
    rm -f "${CACHE_DIR}/maven.tar.gz"

    [ -x "${MAVEN_HOME}/bin/mvn" ] || fail "Maven did not unpack to ${MAVEN_HOME}"
    MVN="${MAVEN_HOME}/bin/mvn"
    log "Maven is at ${MVN}"
}

setup_pyisopep() {
    if [ -x "${PYISOPEP_ENV}/bin/pyisopep" ]; then
        log "pyIsoPEP is already at ${PYISOPEP_ENV}/bin/pyisopep"
        return
    fi

    load_module_if_needed python3 "${PYTHON_MODULE}"
    mkdir -p "${TOOLS_DIR}" "${PIP_CACHE_DIR}"

    log "Creating a pyIsoPEP virtualenv at ${PYISOPEP_ENV}"
    python3 -m venv "${PYISOPEP_ENV}" || fail "Could not create the virtualenv."

    "${PYISOPEP_ENV}/bin/pip" install --quiet --no-cache-dir --upgrade pip
    "${PYISOPEP_ENV}/bin/pip" install --quiet --no-cache-dir pyIsoPEP \
        || fail "Could not install pyIsoPEP."

    "${PYISOPEP_ENV}/bin/pyisopep" --help > /dev/null 2>&1 \
        || fail "pyIsoPEP installed but will not run."
    log "pyIsoPEP is at ${PYISOPEP_ENV}/bin/pyisopep"
    log "Pass it to Context with: -pyisopep ${PYISOPEP_ENV}/bin/pyisopep"
}

check_libs() {
    local missing=0 file
    for file in "${ENCYCLOPEDIA_JAR}" "${MSRAWJAVA_JAR}"; do
        if [ ! -f "${REPO_ROOT}/${file}" ]; then
            printf '  missing: %s\n' "${file}" >&2
            missing=1
        fi
    done
    [ "${missing}" -eq 0 ] \
        || fail "These jars come from the EncyclopeDIA maintainer and are too large for git. Copy them into place and run again."
}
setup_javapot() {
	local revision = "84a59af82c9258cdf8f9774ed0dbc2b648421631"
	local source = "${CACHE_DIR}/JavaPot-${revision}"
	local artifact = "${MAVEN_REPO}/org/searlelab/javapot/1.0.5/javapot-1.0.5.jar"	
	if [ -f "${artifact}" } && { -f "${artifact}.context-source-revision" ] \
	&& [ "$(cat "${artifact}.context-source-revision")" = "${revision}" ]; then
	log "Using JavaPot 1.0.5 from the local Maven cache"
	return 
	fi 
	setup_maven 
	load_module_if_needed javac "${JAVA_MODULE}"
	if [ ! -d "${source}/.git" ]; then 
	git clone	https://github.com/searlelab/JavaPot.git "${source}"
	fi
	git -C "${source}" checkout --detach "${revision}"
	"${MVN}" -B -f- "${source}/pom.xml" "-Dmaven.repo.local=${MAVEN_REPO}" \
	-DskipTests -Dmaven.javac.skip=true install
	printf '%s\n' "${revision}" > "${artifact}.context-source-revision"	
}

build_jar() {
    load_module_if_needed javac "${JAVA_MODULE}"
    command -v javac > /dev/null 2>&1 || fail "Need a JDK, not just a JRE (javac is missing)."
    check_libs
    setup_maven
    setup_javapot
    cd "${REPO_ROOT}"

    log "Building the jar (the first run downloads ~90 dependencies)"
    "${MVN}" -B "-Dmaven.repo.local=${MAVEN_REPO}" -Ppackage clean package

    local jar
    jar="$(ls -1 target/context-*.jar 2> /dev/null | head -1)"
    [ -n "${jar}" ] || fail "Maven finished but produced no target/context-*.jar"

    log "Built ${jar} ($(du -h "${jar}" | cut -f1))"
    java -jar "${jar}" --help > /dev/null || fail "The jar was built but will not run."
    log "The jar runs. Try: java -jar ${jar} percolator -h"
}

build_sif() {
    command -v apptainer > /dev/null 2>&1 || fail "No apptainer on PATH."
    cd "${REPO_ROOT}"
    ls -1 target/context-*.jar > /dev/null 2>&1 || fail "No target/context-*.jar. Run './build.sh jar' first."

    mkdir -p "${APPTAINER_CACHEDIR}"
    log "Building context.sif (pulls a base image and installs pyIsoPEP; give it a few minutes)"
    apptainer build --force context.sif context.def

    log "Built context.sif ($(du -h context.sif | cut -f1))"
    log "It runs. Try: apptainer run --bind \"\$PWD:/data\" --pwd /data context.sif percolator -h"
}

case "${1:-all}" in
    maven)    setup_maven ;;
    pyisopep) setup_pyisopep ;;
    javapot)  setup_javapot ;;
    jar)      build_jar ;;
    sif)      build_sif ;;
    all)      build_jar; build_sif ;;
    *)        fail "Usage: $0 [maven|pyisopep|javapot|jar|sif|all]" ;;
esac
