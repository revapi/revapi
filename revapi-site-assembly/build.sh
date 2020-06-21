#!/bin/sh

npm install @antora/cli @antora/site-generator-default rss asciidoctor-plantuml

node_modules/@antora/cli/bin/antora $@

# a hack before Antora solves https://gitlab.com/antora/antora/-/issues/627
touch build/site/.nojekyll
