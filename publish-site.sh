#!/bin/sh

mvn site site:stage && mvn site-deploy
