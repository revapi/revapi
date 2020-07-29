#!/bin/bash
set -e

echo "The site should be published only when there are no unreleased changes to the docs of the individual modules.
I.e. make sure that you release all the modules that have docs changes."
read -p "Are you sure you want to start the release and site publication process? [y/n]: " start_release
if [ "$start_release" != "y" ] && [ "$start_release" != "Y" ]; then
  echo "Aborting."
  exit 1
fi

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
SITE_revapi_parent=0
SITE_revapi_build_support=0
SITE_revapi_build=0
SITE_revapi_maven_utils=0
SITE_revapi=1
SITE_revapi_basic_features=1
SITE_revapi_reporter_file_base=1
SITE_revapi_reporter_json=1
SITE_revapi_reporter_text=1
SITE_revapi_java_spi=1
SITE_revapi_java=1
SITE_revapi_maven_plugin=1
SITE_revapi_ant_task=1
SITE_revapi_standalone=1

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
  mvn license:format verify -Pantora-release #antora-release makes sure we set the appropriate version in the antora.yml
  version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Release $module-$version"
  git tag "${module}_v${version}"
  ensure_clean_workdir
  mvn -Prelease install deploy -DskipTests # this will also set the version in antora.yml back to master
  mvn versions:set \
    -DnextSnapshot=true
  mvn versions:use-next-snapshots versions:update-parent \
    -DexcludeReactor=false \
    -DallowSnapshots=true \
    -DprocessParent=true \
    -Dincludes='org.revapi:*'
  version=$(xpath -q -e "/project/version/text()" pom.xml)
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

  to_release=$(determine_releases $@)
  cd revapi-site/src/site/modules/news/pages/news
  echo "= Release Notes
:page-publish_date: $(date --rfc-3339=date)
:page-layout: news-article

You're in news. Write release notes and save the file using an appropriate name.
The following modules were released:
$to_release
" \
  | vim -
  git add -A
  git commit -m "Adding release notes for release of $to_release"

  cd "${cwd}/revapi-site-assembly"

  rm -Rf build
  git clone https://github.com/revapi/revapi.github.io.git --depth 1 build/site
  ./build.sh antora-playbook.yaml --stacktrace

  ensure_clean_workdir

  for dep in $to_release; do
    # if the module has a site
    if [ 1 = $(eval "echo \$SITE_$dep") ]; then
      m="$(to_module "$dep")"
      cd "../$m"
      # check that all releases have their mvn sites present in the checkout
      releases=$(git tag | grep ${m}_v)
      for r in $releases; do
        ver=$(echo $r | sed 's/^.*_v//')
        dir="../revapi-site-assembly/build/site/$m/$ver/_attachments"
        check_file="$dir/index.html"
        if [ ! -f $check_file ]; then
          git checkout "${r}"
          # package so that the revapi report can be produced
          mvn package site -DskipTests

          mkdir -p $dir
          cp -R target/site/* $dir
        fi
      done
      # and copy the attachments of the latest version of the module to the "master" version of it
      latestTag=$(git tag -l --sort=creatordate "${m}_v*" | head -1)
      latestVer=$(echo $latestTag | sed 's/^.*_v//')
      rm -Rf "../revapi-site-assembly/build/site/$m/_attachments"
      cp -r "../revapi-site-assembly/build/site/$m/$latestVer/_attachments" "../revapi-site-assembly/build/site/$m"
    fi
  done

  cd "${cwd}/revapi-site-assembly/build/site"

  git add -A
  git commit -m "Site changes for release of $to_release"
  git remote set-url --push origin git@github.com:revapi/revapi.github.io.git
  git push -f origin HEAD:staging

  cd "${cwd}"
  git checkout "$current_branch"
}

do_releases $@
publish_site $@
