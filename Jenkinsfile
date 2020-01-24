def jenkinsfile

def overrides = [
    scriptVersion  : 'v7',
    iq: false,
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    credentialsId: "github",
    checkstyle : false,
    openShiftBuild: false,
    sonarQube: false,
    docs: false,
    javaVersion: 11,
    versionStrategy: [
      [ branch: 'master', versionHint: '1' ]
    ]
]

fileLoader.withGit(config.pipelineScript, config.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}

jenkinsfile.run(config.scriptVersion, config)
