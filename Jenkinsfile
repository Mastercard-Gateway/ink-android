node {
    stage 'Checkout'
    checkout scm

    stage 'Clean'
    sh "./gradlew clean --refresh-dependencies"

    stage 'Build'
    sh "./gradlew ink:assembleRelease"

    stage 'Test'
    androidLint canComputeNew: false, canRunOnFailed: true, defaultEncoding: '', failedNewHigh: '0', healthy: '', pattern: 'ink/build/**/lint-*.xml', unHealthy: '', unstableTotalAll: '200'
    sh "./gradlew ink:lint ink:test"

    stage 'Deploy'
    sh "./gradlew ink:generatePomFileForAarPublication ink:artifactoryPublish"

    stage 'Archive'
    step([$class: 'ArtifactArchiver', artifacts: 'ink/build/outputs/**/*.aar', fingerprint: true])
    properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactNumToKeepStr: '10', numToKeepStr: '10']]])
}
