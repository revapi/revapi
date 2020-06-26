#!/bin/sh

npm install @antora/cli @antora/site-generator-default rss asciidoctor-plantuml antora-site-generator-lunr

DOCSEARCH_ENABLED=true \
DOCSEARCH_ENGINE=lunr \
DOCSEARCH_INDEX_VERSION=latest \
NODE_PATH="$(npm -g root)" \
node_modules/@antora/cli/bin/antora --generator antora-site-generator-lunr $@

# a hack before Antora solves https://gitlab.com/antora/antora/-/issues/627
touch build/site/.nojekyll
