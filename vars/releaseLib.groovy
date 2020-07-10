#!groovy

def call(Closure body) {
    properties([
            parameters([
                    booleanParam(name: 'release', defaultValue: false, description: 'Release? if false, upgrade to next development version only'),
                    string(name: 'repoUrl', description: 'Repo url of the project to release'),
                    string(name: 'branch', defaultValue: 'master', description: 'Specify a branch to release'),
                    string(name: 'version', defaultValue: '', description: 'Version of the project'),
            ])
    ])

    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [
            gitlabCredentialId    : '',

            release               : params.release,
            repoUrl               : params.repoUrl,
            branch                : params.branch,
            version               : params.version,
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node("master") {
        stage('0.Get code from Gitlab') {
            git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialId}", url: "${pipelineParams.repoUrl}"
        }

        if (pipelineParams.release) {
            stage('1. Change Revision') {
                //change revision

                if (!pipelineParams.version?.trim()) {
                    def oldVersion = sh(
                            script: "sed -n 's/.*revision>\\(.*\\)<\\/revision>/\\1/p' pom.xml",
                            returnStdout: true
                    ).trim()
                    pipelineParams.version = nextDevelopmentVersion(oldVersion)
                }

                sh "sed -i 's#<revision.*\$#<revision>${pipelineParams.version}</revision>#' pom.xml"

                sh """
                    git add pom.xml
                    git commit -m "Release v${pipelineParams.version}"
                    git tag -a "v${pipelineParams.version}" -m "Release v${pipelineParams.version}"
                """
            }

        }

        stage('2. Deploy & tag') {

            sh " ./mvnw -B -T 1C clean deploy"
        }

    }

}

private String nextDevelopmentVersion(String oldVersion) {
    oldVersion = oldVersion.replace("-SNAPSHOT", "")
    digits = oldVersion.split("\\.")
    if (digits.length == 3) {
        lastDigit = Integer.valueOf(digits[2])
        return digits[0] + "." + digits[1] + "." + ++lastDigit + "-SNAPSHOT"
    }
    return null
}
