#!/bin/bash
set -e

ALL_MODULES=" $(ls -df1 revapi-* | sed 's/-/_/g') revapi coverage"

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
DEPS_revapi_jackson=("revapi")
DEPS_revapi_json=("revapi_jackson")
DEPS_revapi_yaml=("revapi_jackson")
DEPS_coverage=("revapi_build")
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
ORDER_coverage=3
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
  echo "$b" | grep -q -E "(^| )$a( |$)"
}

function downstream_deps() {
  dep=$(to_dep "$1")
  downs=""
  for d in $ALL_MODULES; do
    ups=$(upstream_deps "$d")
    if contains "$dep" "$ups"; then
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
  if [ $module = "coverage" ]; then
    return
  fi
  ups=$(upstream_deps "$module")

  # make sure we're buildable as is and also that any subsequent builds can access out pre-release artifact that they're
  # probably refer to.
  mvn install -Pfast

  #now let's start the release
  mvn versions:update-parent
  if contains "revapi_build" "$ups"; then
    mvn package revapi:update-versions -Pfast
  fi
  mvn versions:force-releases versions:update-properties -DprocessParent=true -Dincludes="org.revapi:*"
  mvn versions:set -DremoveSnapshot=true
  mvn verify -Pantora-release #antora-release makes sure we set the appropriate version in the antora.yml

  # now we need to use the new version in the whole project so that it is buildable in the current revision
  currentDir=$(pwd)
  cd ..
  mvn versions:use-releases versions:update-properties -DprocessParent=true -DallowDowngrade=true \
    -DallowSnapshots=false -Dincludes="org.revapi:*"
  cd ${currentDir}

  # commit and finish up the release
  version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Release $module-$version"
  git tag "${module}_v${version}"
  ensure_clean_workdir
  mvn -Prelease,fast install deploy

  # set the version to the next snapshot
  mvn versions:set -DnextSnapshot=true
  mvn process-resources -Pfast # reset the version in antora.yml back to main

  # and again, use the new version (the next snapshot) everywhere
  currentDir=$(pwd)
  cd ..
  mvn versions:use-reactor versions:update-properties -DprocessParent=true -DallowSnapshots=true \
    -Dincludes="org.revapi:*" -DexcludeProperties="self-api-check.java-extension-version,self-api-check.maven-version"
  cd ${currentDir}

  version=$(xpath -q -e "/project/version/text()" pom.xml)
  git add -A
  git commit -m "Setting $module to version $version"
  #now we need to install so that the subsequent builds pick up our new version
  mvn install -Pfast
}

function update_module_version() {
  module=$(xpath -q -e "/project/artifactId/text()" pom.xml)
  ups=$(upstream_deps "$module")
  if contains "revapi_build" "$ups"; then
    if [ $module != "coverage" ]; then
      # we want to run license check and revapi
      mvn package revapi:update-versions -DskipTests -Dcheckstyle.skip=true -Denforcer.skip=true -Dformatter.skip=true \
      -Djacoco.skip=true -Dsort.skip=true
    fi
  fi
  cd ..
  mvn validate -Pversion-snapshots
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
  git commit -m "Adding release notes for release of $to_release" || true

  cd "${cwd}/revapi-site-assembly"

  rm -Rf build
  git clone https://github.com/revapi/revapi.github.io.git --depth 1 build/site
  ./build.sh antora-playbook.yaml --stacktrace

  ensure_clean_workdir

  for dep in $to_release; do
    # if the module has a site
    if [ 1 = $(eval "echo \$SITE_$dep") ]; then
      m="$(to_module "$dep")"
      cd "${cwd}/$m"
      # check that all releases have their mvn sites present in the checkout
      releases=$(git tag | grep ${m}_v)
      for r in $releases; do
        ver=$(echo $r | sed 's/^.*_v//')
        dir="../revapi-site-assembly/build/site/$m/$ver/_attachments"
        check_file="$dir/index.html"
        if [ ! -f $check_file ]; then
          git checkout "${r}"
          # package so that the revapi report can be produced
          mvn package site -DskipTests -Pantora-release

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
          <script>location=\"$page\"</script>
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