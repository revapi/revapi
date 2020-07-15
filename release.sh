#!/bin/bash
set -e

ALL_MODULES=" $(ls -df1 revapi-* | sed 's/-/_/g') revapi"

DEPS_revapi_parent=()
DEPS_revapi_site=()
DEPS_revapi_site_assembly=()
DEPS_revapi_site_shared=()
DEPS_revapi_build_support=("revapi_parent")
DEPS_revapi_build=("revapi_parent" "revapi_build_support")
DEPS_revapi_maven_utils=("revapi_build")
DEPS_revapi=("revapi_build")
DEPS_revapi_basic_features=("revapi")
DEPS_revapi_reporter_file_base=("revapi")
DEPS_revapi_reporter_json=("revapi_reporter_file_base")
DEPS_revapi_reporter_text=("revapi_reporter_file_base")
DEPS_revapi_java_spi=("revapi")
DEPS_revapi_java=("revapi_java_spi")
DEPS_revapi_maven_plugin=("revapi_basic_features" "revapi_maven_utils")
DEPS_revapi_ant_task=("revapi_basic_features")
DEPS_revapi_standalone=("revapi_basic_features" "revapi_maven_utils")
ORDER_revapi_parent=0
ORDER_revapi_build_support=1
ORDER_revapi_build=2
ORDER_revapi_maven_utils=3
ORDER_revapi=3
ORDER_revapi_basic_features=4
ORDER_revapi_reporter_file_base=5
ORDER_revapi_reporter_json=6
ORDER_revapi_reporter_text=6
ORDER_revapi_java_spi=4
ORDER_revapi_java=5
ORDER_revapi_maven_plugin=5
ORDER_revapi_ant_task=5
ORDER_revapi_standalone=5

function to_dep() {
  echo "${@//-/_}"
}

function to_module() {
  echo "${@//_/-}"
}

function sort_deps() {
  sorted=""
  for d in $@; do
    order=$(eval "echo \$ORDER_$d")
    sorted="$sorted,$order $d"
  done
  echo "$sorted" | tr "," "\n" | sort | cut -d' ' -f 2 | uniq | sed '/^$/d'
}

function upstream_deps() {
  dep=$(to_dep "$1")
  dep=$(eval "echo \$DEPS_$dep")
  all_deps="${dep}"
  while true; do
    dep=$(eval "echo \$DEPS_$dep")
    if [ -n "$dep" ]; then
      all_deps="${all_deps} ${dep}"
    else
      break
    fi
  done

  echo "${all_deps}" | tr " " "\n" | sort | uniq
}

function contains() {
  a=$1
  b=$2
  if [[ $b == "$a "* ]]; then
    echo 0
  elif [[ $b == *" $a" ]]; then
    echo 0
  elif [[ $b == *" $a "* ]]; then
    echo 0
  else
    echo 1
  fi
}

function downstream_deps() {
  dep=$(to_dep "$1")
  downs=""
  for d in $ALL_MODULES; do
    ups=$(upstream_deps "$d" | tr "\n" " ")
    if [ "$(contains "$dep" "$ups")" -eq 0 ]; then
      downs="$downs $d"
    fi
  done

  echo "$downs" | tr " " "\n" | sort | uniq
}

function collect_release_modules() {
  local to_release=""
  for m in $@; do
    downs=$(downstream_deps $m)
    to_release="$to_release\n$downs"
  done

  echo "$to_release" | sort | uniq
}

function ensure_clean_workdir() {
  changes=$(git status --porcelain | wc -l)
  if [ "$changes" -ne 0 ]; then
    echo "Some changes are not committed."
    exit 1
  fi
}

function release_module() {
  ensure_clean_workdir
  module=$(xpath -q -e "/project/artifactId/text()" pom.xml)
  ups=$(upstream_deps "$module")
  if [ "$(contains "revapi-build" "$ups")" -eq 0 ]; then
    mvn package revapi:update-versions -DskipTests
  fi
  mvn versions:update-parent versions:force-releases -DprocessParent=true -Dincludes="org.revapi:*"
  mvn versions:set -DremoveSnapshot=true
  mvn license:format verify
  version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Release $module-$version"
  git tag "${module}_v${version}"
  ensure_clean_workdir
  mvn -Prelease install deploy -DskipTests
  mvn versions:set \
    -DnextSnapshot=true
  mvn versions:use-next-snapshots versions:update-parent \
    -DexcludeReactor=false \
    -DallowSnapshots=true \
    -DprocessParent=true \
    -Dincludes='org.revapi:*'
  version=$(xpath -q -e "/project/version/text()" pom.xml)
  mvn process-sources # to set the version in antora.yml
  git add -A
  git commit -m "Setting $module to version $version"
  #now we need to install so that the subsequent builds pick up our new version
  mvn install -DskipTests
}

function determine_releases() {
  for m in $@; do
    m=$(to_dep "$m")
    to_release="$to_release $m $(downstream_deps "$m")"
    to_release=$(echo "$to_release" | tr " " "\n")
  done
  to_release=$(sort_deps $to_release)
  echo $to_release
}

function do_releases() {
  CWD=$(pwd)

  to_release=$(determine_releases $@)
  echo "The following modules will be released $(echo "$to_release" | tr "\n" " ")"

  for m in $to_release; do
    m=$(to_module "$m")
    echo "--------- Releasing $m"
    cd "$m"
    release_module "$m"
    cd "$CWD"
  done

  echo $to_release
}

function publish_site() {
  ensure_clean_workdir

  current_branch=$(git rev-parse --abbrev-ref HEAD)

  cwd=$(pwd)

  cd revapi-site/site/modules/news/pages/news
  vim -c ":read \!echo You\'re in news. Write release notes and save the file using an appropriate name."
  git add -A
  git commit -m "Adding release notes"

  cd "${cwd}/revapi-site-assembly"

  ./build.sh antora-playbook.yaml

  ensure_clean_workdir

  to_release=$(determine_releases $@)
  for m in $to_release; do
    m="$(to_module "$m")"
    cd "../$m"
    releases=$(git tag | grep ${m}_v)
    for r in $releases; do
      git checkout "${r}"
      # package so that the revapi report can be produced
      mvn package site -DskipTests
      ver=$(echo $r | sed 's/^.*_v//')

      dir=../revapi-site-assembly/build/site/$m/$ver/_attachments
      mkdir -p $dir
      cp -R target/site/* $dir
    done
  done

  # TODO actually publish the site

  git checkout "$current_branch"
  cd ..
}

do_releases $@
publish_site $@
