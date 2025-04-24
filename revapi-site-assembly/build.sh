#!/bin/sh

DOCKER=podman

if ! which $DOCKER >/dev/null 2>&1; then
  DOCKER=docker
fi

if ! which $DOCKER >/dev/null 2>&1; then
  echo "podman or docker required"
  exit 1
fi

$DOCKER run -p8000:8000 --name local-kroki-installation-for-revapi-build -d docker.io/yuzutech/kroki

npm install @antora/cli @antora/site-generator-default rss asciidoctor-kroki antora-site-generator-lunr

DOCSEARCH_ENABLED=true \
  DOCSEARCH_ENGINE=lunr \
  DOCSEARCH_INDEX_VERSION=latest \
  NODE_PATH="$(npm -g root)" \
  node_modules/@antora/cli/bin/antora --generator antora-site-generator-lunr $@

$DOCKER kill local-kroki-installation-for-revapi-build
