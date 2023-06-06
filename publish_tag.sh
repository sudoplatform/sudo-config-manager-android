#!/bin/sh

set -euo pipefail

while getopts 't:' c
do
  case $c in
    t) TAG=$OPTARG ;;
    *) abort 'Unknown arg'
  esac
done

if [ -z "$TAG" ]; then
  abort "Usage: sh publish_tag.sh -t <tag>"
fi

if [ -z "${GITHUB_ACCESS_TOKEN}" ]; then
    abort "Unable to publish due to missing GITHUB_ACCESS_TOKEN environment variable."
fi

if [ -z "${ORG_GRADLE_PROJECT_signingKey}" ]; then
    abort "Unable to publish due to missing ORG_GRADLE_PROJECT_signingKey environment variable."
fi

NAME=SudoConfigManager
MODULE_NAME=sudoconfigmanager
GITHUB_PROJECT_NAME=sudo-config-manager-android
GITHUB_USERNAME=sudoplatform-engineering

INTERNAL_ONLY_FILES=("CHANGELOG.md" "TestApp" ".gitlab-ci.yml" "publish_tag.sh" "publish-mavencentral.gradle" "${MODULE_NAME}/src/androidTest" "${MODULE_NAME}/src/test" "sonar-project.properties")

RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

export GIT_COMMITTER_NAME="Sudo Platform Engineering"
export GIT_COMMITTER_EMAIL="sudoplatform-engineering@anonyome.com"

export GRADLEW="./gradlew -Dorg.gradle.java.home=$JAVA_HOME"

echo "Using gradle command: $GRADLEW"

abort() {
  announce "${RED}$* -- aborting"
  exit 1
}

announce() {
  echo "${YELLOW}$(date '+%Y-%m-%d %H:%M:%S') $*${NC}"
}

get_gitlab_release_notes() {
  RESPONSE=$(curl -s -k --header "Private-Token: ${API_ACCESS_TOKEN}" https://gitlab.tools.anonyome.com/api/v4/projects/${CI_PROJECT_ID}/releases/${TAG})
  REL_NOTES_JSON=$(printf '%s' "${RESPONSE//$'\n'/}" | jq '.description')
}

push_github_sources() {
  git clone "https://${GITHUB_ACCESS_TOKEN}@github.com/sudoplatform/${GITHUB_PROJECT_NAME}.git" "${GITHUB_PROJECT_NAME}-gh"

  pushd "${GITHUB_PROJECT_NAME}-gh"
  git remote add internal ..
  git fetch internal --tags
  git checkout "${TAG}" -- . # TODO: ensure we checkout tag from internal
  mv README.external.md README.md
  mv settings.external.gradle settings.gradle
  git add .
  git rm -r --cached -- "${INTERNAL_ONLY_FILES[@]}"
  git commit -m "Release ${TAG}" --author "${GIT_COMMITTER_NAME} <${GIT_COMMITTER_EMAIL}>"
  git push
  popd
}

create_github_release() {
  RESPONSE=$(curl -s -u ${GITHUB_USERNAME}:${GITHUB_ACCESS_TOKEN} -d "{\"tag_name\": \"v${TAG}\", \"target_commitish\": \"master\", \"body\": $(printf '%s' "${REL_NOTES_JSON}" | jq "\"Release v${TAG} of ${GITHUB_PROJECT_NAME}\r\n\r\n\" + .") }" https://api.github.com/repos/sudoplatform/"${GITHUB_PROJECT_NAME}"/releases)
  RELEASE_ID=$(printf '%s' "${RESPONSE}" | jq '.id')
}

create_external_maven_release() {
  $GRADLEW ${MODULE_NAME}:assembleRelease
  $GRADLEW -Dorg.gradle.internal.publish.checksums.insecure=true -Ptag=${TAG} -PnexusUsername=${OSSRH_USERNAME} -PnexusPassword=${OSSRH_PASSWORD} ${MODULE_NAME}:publishReleasePublicationToSonatypeRepository
  $GRADLEW -PnexusUsername=${OSSRH_USERNAME} -PnexusPassword=${OSSRH_PASSWORD} closeAndReleaseRepository
}

announce "Retrieving release notes"
get_gitlab_release_notes

announce "The release notes are ${REL_NOTES_JSON}"

announce "Publishing GitHub source snapshot v${TAG} for ${GITHUB_PROJECT_NAME}"
push_github_sources

announce "Creating GitHub release v${TAG} for ${GITHUB_PROJECT_NAME}"
create_github_release

announce "Publishing v${TAG} externally to Maven Central"
create_external_maven_release
