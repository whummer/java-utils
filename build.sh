#!/bin/bash

#mvn clean deploy -Dmaven.repo.local=build -DlocalRepositoryPath=build
mvn -DaltDeploymentRepository=github-repo-releases::default::file:./build clean deploy
