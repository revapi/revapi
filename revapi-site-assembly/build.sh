#!/bin/sh

npm install @antora/cli @antora/site-generator-default rss asciidoctor-kroki antora-site-generator-lunr

DOCSEARCH_ENABLED=true \
DOCSEARCH_ENGINE=lunr \
DOCSEARCH_INDEX_VERSION=latest \
NODE_PATH="$(npm -g root)" \
node_modules/@antora/cli/bin/antora --generator antora-site-generator-lunr $@
