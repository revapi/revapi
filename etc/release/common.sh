#!/bin/bash
set -e

MVN=${MVN:-mvn}

ALL_MODULES=" $(find -maxdepth 1 -type d -name 'revapi-*' | sed -e 's/-/_/g' -e 's|^\./||g') revapi coverage"

UNRELEASED="coverage revapi_site revapi_examples"

DEPS_revapi_parent=""
DEPS_revapi_site="revapi_build"
DEPS_revapi_site_assembly=""
DEPS_revapi_build_support="revapi_parent"
DEPS_revapi_build="revapi_parent revapi_build_support"
DEPS_revapi_maven_utils="revapi_build"
DEPS_revapi="revapi_build"
DEPS_revapi_basic_features="revapi revapi_build"
DEPS_revapi_reporter_file_base="revapi revapi_build"
DEPS_revapi_reporter_json="revapi_reporter_file_base revapi_build revapi"
DEPS_revapi_reporter_text="revapi_reporter_file_base revapi_build revapi"
DEPS_revapi_java_spi="revapi revapi_build"
DEPS_revapi_java="revapi_java_spi revapi_build revapi_basic_features"
DEPS_revapi_maven_plugin="revapi_basic_features revapi_maven_utils revapi_build revapi_java revapi_java_spi revapi revapi_reporter_text"
DEPS_revapi_ant_task="revapi_basic_features revapi revapi_build"
DEPS_revapi_standalone="revapi_basic_features revapi_maven_utils revapi revapi_build"
DEPS_revapi_jackson="revapi revapi_build"
DEPS_revapi_json="revapi_jackson revapi_build"
DEPS_revapi_yaml="revapi_jackson revapi_build"
DEPS_coverage="revapi_build revapi revapi_ant_task revapi_basic_features revapi_jackson revapi_java revapi_java_spi revapi_json revapi_maven_plugin revapi_maven_utils revapi_reporter_file_base revapi_reporter_json revapi_reporter_text revapi_yaml"
DEPS_revapi_examples=$DEPS_coverage

RELEASE_DEPS_revapi_parent=$DEPS_revapi_parent
RELEASE_DEPS_revapi_site=$DEPS_revapi_site
RELEASE_DEPS_revapi_site_assembly=$DEPS_revapi_site_assembly
RELEASE_DEPS_revapi_examples=$DEPS_revapi_examples
RELEASE_DEPS_revapi_build_support=$DEPS_revapi_build_support
RELEASE_DEPS_revapi_build=$DEPS_revapi_build
RELEASE_DEPS_revapi_maven_utils=$DEPS_revapi_maven_utils
RELEASE_DEPS_revapi=$DEPS_revapi
RELEASE_DEPS_revapi_basic_features=$DEPS_revapi_basic_features
RELEASE_DEPS_revapi_reporter_file_base=$DEPS_revapi_reporter_file_base
RELEASE_DEPS_revapi_reporter_json=$DEPS_revapi_reporter_json
RELEASE_DEPS_revapi_reporter_text=$DEPS_revapi_reporter_text
RELEASE_DEPS_revapi_java_spi=$DEPS_revapi_java_spi
RELEASE_DEPS_revapi_java=$DEPS_revapi_java
RELEASE_DEPS_revapi_maven_plugin="revapi_basic_features revapi_maven_utils revapi_build revapi revapi_reporter_text"
RELEASE_DEPS_revapi_ant_task=$DEPS_revapi_ant_task
RELEASE_DEPS_revapi_standalone=$DEPS_revapi_standalone
RELEASE_DEPS_revapi_jackson=$DEPS_revapi_jackson
RELEASE_DEPS_revapi_json=$DEPS_revapi_json
RELEASE_DEPS_revapi_yaml=$DEPS_revapi_yaml
RELEASE_DEPS_coverage=$DEPS_coverage

ORDER_revapi_parent=0
ORDER_revapi_build_support=1
ORDER_revapi_build=2
ORDER_revapi_maven_utils=3
ORDER_revapi=3
ORDER_revapi_basic_features=4
ORDER_revapi_reporter_file_base=4
ORDER_revapi_reporter_json=5
ORDER_revapi_reporter_text=5
ORDER_revapi_java_spi=4
ORDER_revapi_java=5
ORDER_revapi_maven_plugin=6
ORDER_revapi_ant_task=5
ORDER_revapi_standalone=5
ORDER_revapi_jackson=4
ORDER_revapi_json=5
ORDER_revapi_yaml=5
ORDER_revapi_examples_parent=7
ORDER_coverage=7

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
SITE_revapi_jackson=1
SITE_revapi_json=1
SITE_revapi_yaml=1
SITE_revapi_examples_parent=0
SITE_coverage=0

function to_dep() {
  echo "${@//-/_}"
}

function to_module() {
  echo "${@//_/-}"
}

function sort_deps() {
  local sorted=""
  for d in $@; do
    local order=$(eval "echo \$ORDER_$d")
    sorted="$sorted,$order $d"
  done
  echo "$sorted" | tr "," "\n" | sort | cut -d' ' -f 2 | uniq | sed '/^$/d'
}

function upstream_deps() {
  local dep=$(to_dep "$1")
  local prefix="DEPS"
  if [ -n "$2" ]; then
    prefix="$2"
  fi

  dep=$(eval "echo \$${prefix}_${dep}")
  local all_deps="${dep}"
  while true; do
    local dep=$(eval "echo \$${prefix}_${dep}")
    if [ -n "$dep" ]; then
      all_deps="${all_deps} ${dep}"
    else
      break
    fi
  done

  echo "${all_deps}" | tr " " "\n" | sort | uniq
}

function direct_upstream_deps() {
  local dep=$(to_dep "$1")
  eval "echo \$DEPS_$dep"
}

function contains() {
  local a=$1
  local b=$2
  echo "$b" | grep -q -E "(^| )$a( |$)"
}

function downstream_deps() {
  local dep=$(to_dep "$1")
  local downs=""
  for d in $ALL_MODULES; do
    local ups=$(upstream_deps "$d")
    if contains "$dep" "$ups"; then
      downs="$downs $d"
    fi
  done

  echo "$downs" | tr " " "\n" | sort | uniq
}

function downstream_release_deps() {
  local dep=$(to_dep "$1")
  local downs=""
  for d in $ALL_MODULES; do
    local ups=$(upstream_deps "$d" "RELEASE_DEPS")
    if contains "$dep" "$ups"; then
      downs="$downs $d"
    fi
  done

  echo "$downs" | tr " " "\n" | sort | uniq
}

function direct_downstream_deps() {
  local dep=$(to_dep "$1")
  local downs=""
  for d in $ALL_MODULES; do
    local ups=$(direct_upstream_deps "$d")
    if contains "$dep" "$ups"; then
      downs="$downs $d"
    fi
  done

  echo "$downs" | tr " " "\n" | sort | uniq
}

function ensure_clean_workdir() {
  local changes=$(git status --porcelain | wc -l)
  if [ "$changes" -ne 0 ]; then
    echo "Some changes are not committed."
    exit 1
  fi
}

function release_module() {
  __result_module_name=$1
  __result_module_version=$2

  eval $__result_module_name=""
  eval $__result_module_version=""

  ensure_clean_workdir
  local module=$(xpath -q -e "/project/artifactId/text()" pom.xml)
  if contains $(to_dep $module) "$UNRELEASED"; then
    return
  fi

  local ups=$(upstream_deps "$module")
  local downs=$(direct_downstream_deps "$module")

  # make sure we're buildable as is and also that any subsequent builds can access out pre-release artifact that they're
  # probably refer to.
  ${MVN} install -Pfast

  #now let's start the release
  if contains "revapi_build" "$ups"; then
    ${MVN} package revapi:update-versions -Pfast -Drevapi.skip=false
  fi

  ${MVN} validate versions:force-releases versions:update-parent versions:update-properties -Prelease-versions
  ${MVN} versions:set -DremoveSnapshot=true
  ${MVN} install -Pdocs-release #docs-release makes sure we set the appropriate version in the antora.yml

  # now we need to use the new version in the whole project so that it is buildable in the current revision
  local currentDir=$(pwd)
  cd ..
  for m in $downs; do
    echo "----------------- Updating direct dependency: $m"
    cd $(to_module $m)
    if [ ! -f pom.xml ]; then
      cd ..
      continue
    fi
    local goals="validate versions:force-releases versions:update-properties"
    local parent=$(xpath -q -e "/project/parent/artifactId/text()" pom.xml)
    if [ $parent = $module ]; then
      goals="$goals versions:update-parent"
    fi
    ${MVN} $goals -Prelease-versions -Dincludes="org.revapi:$module"
    cd ..
  done
  cd ${currentDir}

  # commit and finish up the release
  local release_version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Release $module-$release_version"
  git tag "${module}_v${release_version}"
  ensure_clean_workdir
  ${MVN} -Prelease,fast install deploy

  # set the version to the next snapshot
  ${MVN} versions:set -DnextSnapshot=true

  ${MVN} validate versions:use-latest-versions versions:update-properties versions:update-parent -Pnext-versions
  # reset the version in antora.yml back to main and install our next version so that validate can pick it up
  ${MVN} install -Pfast

  # and again, use the new version (the next snapshot) everywhere
  currentDir=$(pwd)
  cd ..
  for m in $downs; do
    cd $(to_module $m)
    if [ ! -f pom.xml ]; then
      cd ..
      continue
    fi

    ${MVN} validate versions:use-latest-versions versions:update-properties versions:update-parent -Pnext-versions -Dincludes="org.revapi:$module"
    cd ..
  done
  cd ${currentDir}

  local version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Setting $module to version $version"

  #now we need to install so that the subsequent builds pick up our new version
  ${MVN} install -Pfast

  eval $__result_module_name="'$module'"
  eval $__result_module_version="'$release_version'"
}

function update_module_version() {
  module=$(xpath -q -e "/project/artifactId/text()" pom.xml)
  if contains $(to_dep $module) "$UNRELEASED"; then
    return
  fi
  ups=$(upstream_deps "$module")
  if contains "revapi_build" "$ups"; then
    if [ $module != "coverage" ]; then
      # we want to run license check and revapi
      ${MVN} revapi:update-versions -DskipTests -Dcheckstyle.skip=true -Denforcer.skip=true -Dformatter.skip=true \
      -Djacoco.skip=true -Dsort.skip=true
      ${MVN} install -DskipTests -Dcheckstyle.skip=true -Denforcer.skip=true -Dformatter.skip=true \
      -Djacoco.skip=true -Dsort.skip=true
    fi
  fi
  cd ..
  ${MVN} validate versions:use-latest-versions versions:update-properties versions:update-parent -Pnext-versions
}

function determine_releases() {
  for m in $@; do
    m=$(to_dep "$m")
    to_release="$to_release $m $(downstream_release_deps "$m")"
    to_release=$(echo "$to_release" | tr " " "\n")
  done
  to_release=$(sort_deps $to_release)
  echo $to_release
}

function before_releases() {
  hasSnapshots=$(mvn help:evaluate -Dexpression=project.repositories 2>/dev/null | grep -E '^\s*<' --color=never | \
    xpath -q -e "/repositories/repository/snapshots/enabled[text() = 'true' and ../../url[text() = 'https://repo.maven.apache.org/maven2']")

  if [ -z "$hasSnapshots" ]; then
    >&2 echo "Maven Central snapshot repository needs to be enabled otherwise things don't work!"
    exit 1
  fi

  rm -Rf ~/.m2/repository/org/revapi
  ${MVN} clean install -Pfast
}

function do_releases() {
  CWD=$(pwd)

  before_releases

  to_release=$(determine_releases $@)
  echo "The following modules will be released $(echo "$to_release" | tr "\n" " ")"

  released_maven_plugin_version=""
  released_revapi_java_version=""
  for m in $to_release; do
    m=$(to_module "$m")
    echo "--------- Releasing $m"
    cd "$m"
    release_module module_name module_version
    if [ "$module_name" == "revapi-maven-plugin" ]; then
      released_maven_plugin_version=$module_version
    fi
    if [ "$module_name" == "revapi-java" ]; then
      released_revapi_java_version=$module_version
    fi
    cd "$CWD"
  done

  dirty=0
  if [ -n "$released_maven_plugin_version" ]; then
    cd revapi-build
    ${MVN} versions:set-property -Dproperty=self-api-check.maven-version -DnewVersion="$released_maven_plugin_version"
    cd ..
    dirty=1
  fi

  if [ -n "$released_revapi_java_version" ]; then
    cd revapi-build
    ${MVN} versions:set-property -Dproperty=self-api-check.java-extension-version -DnewVersion="$released_revapi_java_version"
    cd ..
    dirty=1
  fi

  if [ $dirty -eq 1 ]; then
    git add -A
    git commit -m "Updating revapi-build to use the latest versions for the self-check"
  fi
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
  git commit -m "Adding release notes for release of $to_release" || true

  cd "${cwd}/revapi-site-assembly"

  rm -Rf build
  git clone https://github.com/revapi/revapi.github.io.git --depth 1 build/site
  ./build.sh antora-playbook.yaml --stacktrace

  ensure_clean_workdir

  # some releases were released with snapshot deps... let's no try to fix that and just skip javadoc generation for them
  no_javadocs="revapi-maven-plugin:0.13.3 revapi-maven-plugin:0.13.4 revapi-maven-plugin:0.13.5 revapi-maven-plugin:0.13.6"

  for dep in $to_release; do
    # if the module has a site
    if [ 1 = $(eval "echo \$SITE_$dep") ]; then
      m="$(to_module "$dep")"
      cd "${cwd}/$m"
      # check that all releases have their mvn sites present in the checkout
      releases=$(git tag | grep ${m}_v)
      for r in $releases; do
        ver=$(echo $r | sed 's/^.*_v//')
        if [[ $no_javadocs == *"${m}:${ver}"* ]]; then
          echo "Skipping javadoc generation for the known botched release: $m:$ver"
          continue
        fi
        dir="../revapi-site-assembly/build/site/$m/$ver/_attachments"
        check_file="$dir/index.html"
        if [ ! -f $check_file ]; then
          git checkout "${r}"
          # package so that the revapi report can be produced
          ${MVN} package site -DskipTests -Pdocs-release

          mkdir -p $dir
          cp -R target/site/* $dir
        fi
      done

      # and copy the latest version of the module to the "main" version of it
      allTags=$(git tag -l "${m}_v*")
      latestTag=$(git tag -l --sort=creatordate "${m}_v*" | tail -1)
      latestVer=$(echo $latestTag | sed 's/^.*_v//')

      cd "../revapi-site-assembly/build/site/$m"

      # first delete anything that doesn't belong in the module directory - e.g. only leave the version dirs
      for f in $(ls); do
        keep=0
        name=$(basename $f)
        for tag in $allTags; do
          tagDir=$(echo $tag | sed 's/^.*_v//')
          if [ $name = $tagDir ]; then
            keep=1
            break
          fi
        done

        if [ $keep -eq 0 ]; then
          rm -Rf $f
        fi
      done

      # now create redirect pages to all pages in the latest released version
      # this will give us "stable" urls to be referenced from the outside
      for page in $(find "$latestVer" -type f -name '*.html' -not -path '*/_attachments/*'); do
        pageName=$(basename $page)
        pageDir=$(dirname $page)
        dir=$(realpath $pageDir --relative-to=$latestVer)
        mkdir -p $dir
        echo "
          <!DOCTYPE html>
          <meta charset=\"utf-8\">
          <script>location=\"$page\" + location.hash</script>
          <meta http-equiv=\"refresh\" content=\"0; url=$page\">
          <meta name=\"robots\" content=\"noindex\">
          <title>Redirect Notice</title>
          <h1>Redirect Notice</h1>
          <p>The page you requested has been relocated <a href=\"$page\">here</a>.</p>" > "$dir/$pageName"
      done
    fi
  done

  cd "${cwd}/revapi-site-assembly/build/site"

  git add -A
  git commit -m "Site changes for release of $to_release"
  git remote set-url --push origin git@github.com:revapi/revapi.github.io.git
  git push -f origin HEAD:staging

  cd "${cwd}"
  git reset --hard
  git checkout "$current_branch"
}

function update_versions() {
  to_update=$(determine_releases $@)
  echo "The following modules will have versions updated $(echo "$to_update" | tr "\n" " ")"

  for m in $to_update; do
    m=$(to_module "$m")
    echo "--------- Updating $m"
    cd "$m"
    update_module_version "$m"
    cd "$CWD"
  done
}