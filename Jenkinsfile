properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(abortPrevious: true)
])

def runTests(Map params = [:]) {
  return {
    def agentContainerLabel = 'maven-' + params['jdk']
    if (params['platform'] == 'windows') {
      agentContainerLabel += '-windows'
    }
    boolean publishing = params['jdk'] == 21 && params['platform'] == 'linux'
    node(agentContainerLabel) {
      timeout(time: 1, unit: 'HOURS') {
        def stageIdentifier = params['platform'] + '-' + params['jdk']
        stage("Checkout (${stageIdentifier})") {
          checkout scm
        }
        stage("Build (${stageIdentifier})") {
          ansiColor('xterm') {
            def args = ['-Dstyle.color=always', '-Prun-its', '-Dmaven.test.failure.ignore', 'clean', 'install', 'site']
            if (publishing) {
              args += '-Dset.changelist'
            }
            // Needed for correct computation of JenkinsHome in RunMojo#execute.
            withEnv(['JENKINS_HOME=', 'HUDSON_HOME=']) {
              infra.runMaven(args, params['jdk'], null, null, false)
            }
          }
        }
        stage("Archive (${stageIdentifier})") {
          junit 'target/invoker-reports/TEST-*.xml'
          if (publishing) {
            infra.prepareToPublishIncrementals()
          }
        }
      }
    }
  }
}

parallel(
    'linux-21': runTests(platform: 'linux', jdk: 21),
    'windows-17': runTests(platform: 'windows', jdk: 17)
)
infra.maybePublishIncrementals()
