#!groovy

def call(Closure body) {

    def pipelineParams = [
            mavenSettingsXmlFileId     : '',
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
            yamlBranch                 : 'dev'
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()



    node('master') {

       try{
            def targetModule
            stage('0.Get code from Gitlab') {

                git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
                int index
                def str = pipelineParams.repoUrl
                index = str.indexOf("/")
                while (index >= 0){
                    str = str.substring(index+1);
                    index = str.indexOf("/")
                }
                targetModule = str.replaceAll(".git","").replaceAll("_","-")
                echo "targetModule: ${targetModule}"

                if ("${pipelineParams.commitId}"?.trim()) {
                    sh "git checkout -b temp ${pipelineParams.commitId}"
                }
                echo "###############################################################"
                sh "git log -n 5"
                echo "###############################################################"
            }

            def imageName
            def buildTag
            stage('2.Prepare') {


                pipelineParams.each { key, val -> println "Parameter -> ${key}:${val}" }

                if (pipelineParams.deployToUat) {
                    pipelineParams.put("imagePrefix", pipelineParams.imagePrefixUat)
                }
                imageName = "${pipelineParams.imagePrefix}/mk-${targetModule}"
                echo "Docker image name: ${imageName}"

                buildTag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                echo "build tag: ${buildTag}"
            }

            stage('3.Run build and unit tests') {
                sh "npm cache verify"
                sh "npm install -S --verbose"
                sh "npm run build"

            }

            def builtImage
            stage('4.Build docker images') {

                dir("${WORKSPACE}/") {


                    builtImage = docker.build("${imageName}:${buildTag}", "--network host .")
                }

            }
            stage('5.Publish Docker images [and Helm Chart]') {

                sh "echo ${pipelineParams.dockerRegistry}"
                docker.withRegistry(pipelineParams.dockerRegistry, pipelineParams.dockerRegistryCredentialsId) {

                    builtImage.push()
                    builtImage.push('latest')

                }

            }

            stage('7.Deploy') {
                echo "Deploy to ${pipelineParams.deployNamespace}"

                withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                    pipelineParams.put("deployImage", imageName)
                    pipelineParams.put("deployTag", buildTag)
                }


            }

            stage('9.Apply to k8s'){
                if (!pipelineParams.deployToUat) {

                    def tmpYamls = "tmp-yamls"
                    pipelineParams.put("deployRepoUrl", "ssh://git@127.0.0.1/devops/yamls-for-deployment.git")
                    def image = ""
                    stage('1. Prepare yamls') {
                        if (pipelineParams.deployImage.startsWith(pipelineParams.imagePrefix)) {
                            image = pipelineParams.deployImage
                        } else {
                            image = pipelineParams.imagePrefix + "/" + pipelineParams.deployImage
                        }
                        image = image + ":" + pipelineParams.deployTag
                        println("[image --> " + image + "]")

                    }
                    stage('0.Get yamls from Gitlab') {
                        def deployEnv = pipelineParams.deployNamespace.replaceAll("-" + pipelineParams.merchantAccount,"")
                        echo "deployEnv: ${deployEnv}"
                        dir(tmpYamls) {
                            git branch: pipelineParams.yamlBranch, credentialsId: pipelineParams.gitlabCredentialsId, url: pipelineParams.deployRepoUrl
                        }
                        tmpYamls += "/" + targetModule

                        dir(tmpYamls) {
                            withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                                sh "sed -i 's#{appImage}#$image#g' deployment.yaml"
                                sh "sed -i 's#{deployEnv}#$deployEnv#g;s#{merchant}#$pipelineParams.merchantAccount#g' ingress.yaml"
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
