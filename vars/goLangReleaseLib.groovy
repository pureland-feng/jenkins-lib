#!groovy

def call(Closure body) {

    def pipelineParams = [
            gitlabCredentialsId        : '',
            dockerRegistryCredentialsId: "docker-registry-login",
            dockerRegistry             : '127.0.0.1',
            imagePrefix                : '127.0.0.1',
            imagePrefixUat             : '127.0.0.1',
            k8sApiServerUrl            : 'https://127.0.0.1:6443',

            repoUrl                    : '',
            branch                     : 'dev',
            commitId                   : '',

            deployNamespace            : 'dev',
            merchantAccount            : '',
            dockerFileInRoot           : false
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node('master') {

        try{
            def projectName
            def buildTag
            stage('Get code from Gitlab') {
                sh "mkdir -p src"
                dir("src"){
                    git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
                    if ("${pipelineParams.commitId}"?.trim()) {
                        sh "git checkout -b temp ${pipelineParams.commitId}"
                    }
                    sh "git submodule init"
                    sh "git submodule update"
                    buildTag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                }

                int index
                def str = pipelineParams.repoUrl
                index = str.indexOf("/")
                while (index >= 0){
                    str = str.substring(index+1);
                    index = str.indexOf("/")
                }
                projectName = str.replaceAll(".git","").toLowerCase()

            }

            stage('Run build') {

                dir("src"){

                    sh "go build main.go"
                    sh """
                          sshpass -p 'abcd@135' ssh -p 9988 root@172.18.3.114 /goproject/${projectName}/${projectName}_stop.sh
                          sshpass -p 'abcd@135' scp -P 9988 ./main root@172.18.3.114:/goproject/${projectName}/main
                          sshpass -p 'abcd@135' ssh -p 9988 root@172.18.3.114 chmod u+x /goproject/${projectName}/main
                          sshpass -p 'abcd@135' ssh -p 9988 root@172.18.3.114 /goproject/${projectName}/${projectName}_start.sh
                    """
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
