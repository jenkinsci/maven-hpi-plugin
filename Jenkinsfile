properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(abortPrevious: true)
])

def runTests(Map params = [:]) {
  return {
    def agentContainerLabel = params['jdk'] == 8 ? 'maven' : 'maven-' + params['jdk']
    boolean publishing = params['jdk'] == 8
    node(agentContainerLabel) {
      timeout(time: 1, unit: 'HOURS') {
        def stageIdentifier = params['platform'] + '-' + params['jdk']
        stage("Checkout (${stageIdentifier})") {
          checkout scm
        }
        stage("Build (${stageIdentifier})") {
          ansiColor('xterm') {
            sh 'env | grep JENKINS'
            def args = ['-Dstyle.color=always', '-Prun-its', '-Dmaven.test.failure.ignore', 'clean', 'install', 'site']
            if (publishing) {
              args += '-Dset.changelist'
            }
            infra.runMaven(args, params['jdk'])
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
    'linux-8': runTests(platform: 'linux', jdk: 8),
    'linux-11': runTests(platform: 'linux', jdk: 11)
)
infra.maybePublishIncrementals()
