#!/usr/bin/env bash
read -r libs<'classpath.conf';
java -classpath ${libs} house.House ${1} ${2}