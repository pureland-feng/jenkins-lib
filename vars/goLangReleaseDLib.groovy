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
                    echo "########################################################"
                    sh "git log -n 5"
                    echo "########################################################"
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
                    sh "go build -ldflags '-w -s' -o main"
                    sh "upx main"
                }
            }

            stage('4.Build docker images') {

                dir("src") {
                    imageName = "${pipelineParams.imagePrefix}/${projectName}"
                    builtImage = docker.build("${imageName}:${buildTag}", "--network host  --build-arg MAIN_FILE=main --build-arg MERCHANT_ACCOUNT=${pipelineParams.merchantAccount} .")
                }

            }

            stage('Publish Docker images [and Helm Chart]') {

                sh "echo ${pipelineParams.dockerRegistry}"
                docker.withRegistry(pipelineParams.dockerRegistry, pipelineParams.dockerRegistryCredentialsId) {

                    builtImage.push()
                    builtImage.push('latest')

                }

            }

            stage('Deploy') {
                echo "Deploy to ${pipelineParams.deployNamespace}"

                withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                    pipelineParams.put("deployImage", imageName)
                    pipelineParams.put("deployTag", buildTag)
                }


            }

            stage('Apply to k8s'){
                if (!pipelineParams.deployToUat) {

                    def tmpYamls = "tmp-yamls"
                    pipelineParams.put("deployRepoUrl", "ssh://git@127.0.0.1/devops/yamls-for-deployment.git")
                    pipelineParams.put("deployBranch", "dev")
                    def image = ""
                    def hostNameSuffix = ".k8s-dev.com"
                    def hostName = ""
                    stage('Prepare yamls') {
                        if (pipelineParams.deployImage.startsWith(pipelineParams.imagePrefix)) {
                            image = pipelineParams.deployImage
                        } else {
                            image = pipelineParams.imagePrefix + "/" + pipelineParams.deployImage
                        }
                        image = image + ":" + pipelineParams.deployTag
                        println("[image --> " + image + "]")

                        hostName = pipelineParams.deployNamespace + "-" + projectName + hostNameSuffix
                    }
                    stage('Get yamls from Gitlab') {
                        def deployEnv = pipelineParams.deployNamespace.replaceAll("-" + pipelineParams.merchantAccount,"")
                        echo "deployEnv: ${deployEnv}"
                        dir(tmpYamls) {
                            git branch: pipelineParams.deployBranch, credentialsId: pipelineParams.gitlabCredentialsId, url: pipelineParams.deployRepoUrl
                        }
                        tmpYamls += "/" + projectName

                        dir(tmpYamls) {
                            withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                                sh "sed -i 's#{appImage}#$image#g' deployment.yaml"
                                sh "sed -i 's#{hostName}#$hostName#g' ingress.yaml"
                                sh "kubectl -n ${pipelineParams.deployNamespace} apply -f ."
                            }
                        }
                    }
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
