#!/bin/bash
set -e

. "${BASH_SOURCE%/*}/common.sh"

cd $@
release_module $@
