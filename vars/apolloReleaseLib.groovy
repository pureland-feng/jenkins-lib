#!groovy

def call(Closure body) {

    properties([
            parameters([
                    string(name: 'portalUrl', defaultValue: "http://127.0.0.1:8070/", description: ''),
                    choice(choices: ['projectAppId1', 'projectAppId2'],name: 'appId', description: ''),
                    choice(choices: ['DEV','TEST','FAT','HOTFIX','UAT','PRO'], name: 'env', description: ''),
                    choice(choices: ['application','namespaceName1','namespaceName2'], name: 'namespaceName', description: ''),

            ])
    ])

    def pipelineParams = [
            gitlabCredentialsId        : '',
            repoUrl                    : 'ssh://git@127.0.0.1/devops/apollo-manager.git',
            branch                     : 'master',
            token                      : '3b2c57899c6f8df626f677a99ed3c7d19237858e',
            portalUrl                  : params.portalUrl,
            appId                      : params.appId,
            env                        : params.env,
            namespaceName              : params.namespaceName,
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node('master') {

        try{
            stage('0.Get code from Gitlab') {

                git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
            }
            stage('release') {
                try{
                    sh "mvn clean package -Dmaven.test.skip"

                    git branch: "${pipelineParams.env}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "ssh://git@127.0.0.1/devops/apollo-configuration.git"

                    def jenkinsUser
                    wrap([$class: 'BuildUser']) {
                        jenkinsUser = env.BUILD_USER_ID
                    }

                    sh "java -DportalUrl=${pipelineParams.portalUrl} -Dtoken=${pipelineParams.token} -DpropertiesPath=${WORKSPACE}/${pipelineParams.appId}/${pipelineParams.namespaceName}.properties -DappId=${pipelineParams.appId} -Denv=${pipelineParams.env} -DnamespaceName=${pipelineParams.namespaceName} -DjenkinsUser=${jenkinsUser} -jar ./target/apollo-manager-1.0-SNAPSHOT.jar"
                }finally{
                    deleteDir()
                }
            }
        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }

}
