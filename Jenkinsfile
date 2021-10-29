#!/usr/bin/env groovy
def config = [
    scriptVersion  : 'v7',
    iqOrganizationName: "Team AOS",
    credentialsId: "github",
    compilePropertiesIq: "-x test",
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    openShiftBuild: false,
    checkstyle : false,
    javaVersion: 11,
    jiraFiksetIKomponentversjon: true,
    chatRoom: "#aos-notifications",
    versionStrategy: [
        [ branch: 'master', versionHint: '1' ]
    ]
]

fileLoader.withGit(config.pipelineScript, config.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}

jenkinsfile.gradle(config.scriptVersion, config)
