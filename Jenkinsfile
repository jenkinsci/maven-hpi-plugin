properties([
  buildDiscarder(logRotator(numToKeepStr: '20')),
  disableConcurrentBuilds(abortPrevious: true)
])

def runTests(Map params = [:]) {
  def agentContainerLabel = params['jdk'] == 8 ? 'maven' : 'maven-' + params['jdk']
  if (params['platform'] == 'windows') {
    agentContainerLabel += '-windows'
  }
  node(agentContainerLabel) {
    timeout(time: 1, unit: 'HOURS') {
      ansiColor('xterm') {
        withEnv(['MAVEN_OPTS=-Djansi.force=true']) {
          def stageIdentifier = params['platform'] + '-' + params['jdk']
          stage("Checkout (${stageIdentifier})") {
            checkout scm
          }
          stage("Build (${stageIdentifier})") {
            sh 'mvn -B -Dstyle.color=always -ntp -Prun-its -Dmaven.test.failure.ignore clean install site'
          }
          stage("Archive (${stageIdentifier})") {
            junit 'target/invoker-reports/TEST-*.xml'
          }
        }
      }
    }
  }
}

parallel(
    'linux-8': { runTests(platform: 'linux', jdk: 8) },
    'linux-11': { runTests(platform: 'linux', jdk: 11) },
    'windows-11': { runTests(platform: 'windows', jdk: 11) },
    'linux-17': { runTests(platform: 'linux', jdk: 17) }
)
