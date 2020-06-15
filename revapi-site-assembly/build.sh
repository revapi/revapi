#!/bin/sh

npm install @antora/cli @antora/site-generator-default rss asciidoctor-plantuml

node_modules/@antora/cli/bin/antora $@
