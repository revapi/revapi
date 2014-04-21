#!/bin/sh

mvn -Psite site site:stage && mvn -Psite site-deploy
