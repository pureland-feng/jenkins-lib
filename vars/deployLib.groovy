#!groovy

def call(Closure body) {

    def pipelineParams = [
            gitlabCredentialsId        : '',
            repoUrl                    : '',
            branch                     : 'dev',
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node('master') {

        try{
            stage('0.Get code from Gitlab') {
                git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
            }

            stage('1.deploy') {

                sh "chmod u+x ./mvnw"
                sh "./mvnw clean deploy -Dmaven.test.skip=true"

            }
        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }

}
