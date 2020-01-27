#!/usr/bin/env groovy
def config = [
        scriptVersion  : 'v7',
        iq: false,
        credentialsId: "github",
        deployTo: 'maven-central',
        openShiftBuild: false,
        checkstyle : false,
        javaVersion : 11,
        docs: false,
        sonarQube: false,
        pipelineScript : 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
        versionStrategy : [
                [branch: 'master', versionHint: '1']
        ]
]

fileLoader.withGit(config.pipelineScript, config.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}

jenkinsfile.run(config.scriptVersion, config)
