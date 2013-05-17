# Nuxeo Gradle Plugin

## Install

```
gradle install
```

## Use it

```
apply plugin: 'maven'

buildscript {
    repositories {
        mavenLocal()
    }
    dependencies {
        classpath group: 'org.nuxeo.gradle.plugin', name: 'nuxeo-gradle-plugin', version: '1.0-SNAPSHOT'
    }
}

apply plugin: 'nuxeo'

nuxeo {
    sdk="<your sdk path>"     // ex: /opt/nuxeo-cap-5.6-tomcat-sdk
    version="<nuxeo version>" // ex: "5.6"
}

...

```

## Tasks

- nuxeo-start

- nuxeo-stop

- nuxeo-configure

- nuxeo-reload

- nuxeo-shell-ui
