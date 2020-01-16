def jenkinsfile

def overrides = [
    scriptVersion  : 'v7',
    iqOrganizationName: "Team AOS",
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

fileLoader.withGit(overrides.pipelineScript, overrides.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(overrides.scriptVersion, overrides)
