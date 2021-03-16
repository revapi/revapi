#!/bin/bash
set -e

echo "The site should be published only when there are no unreleased changes to the docs of the individual modules.
I.e. make sure that you release all the modules that have docs changes."
read -p "Are you sure you want to start the release and site publication process? [y/n]: " start_release
if [ "$start_release" != "y" ] && [ "$start_release" != "Y" ]; then
  echo "Aborting."
  exit 1
fi

. "${BASH_SOURCE%/*}/common.sh"

do_releases $@
publish_site $@

