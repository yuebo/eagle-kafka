#!/usr/bin/env bash
rm -f *.jar
mvn clean install -f ../pom.xml -P sit -Dmaven.test.skip=true
cp ../target/ke.war ke.war
docker build . -t ke
