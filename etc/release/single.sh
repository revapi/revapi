#!/bin/bash
set -e

. "${BASH_SOURCE%/*}/common.sh"

before_releases

cd $@
release_module $@
