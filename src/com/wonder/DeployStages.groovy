package com.wonder;

def deployStages(pipelineParams) {
    pipelineParams.put("deployRepoUrl", "ssh://git@127.0.0.1/devops/yamls-for-deployment.git")

    return {
        pipelineParams.each { key, val -> println "[Parameter::${key} --> ${val}]" }
        def tmpYamls = "tmp-yamls"
        stage('0.Get yamls from Gitlab') {
            dir(tmpYamls) {
                git branch: pipelineParams.yamlBranch, credentialsId: pipelineParams.gitlabCredentialsId, url: pipelineParams.deployRepoUrl
            }
        }

        def image = ""
        def hostNameSuffix = ".k8s-dev.com"
        def hostName = ""
        def targetModule = ""
        stage('1. Prepare yamls') {
            if (pipelineParams.deployImage.startsWith(pipelineParams.imagePrefix)) {
                image = pipelineParams.deployImage
                targetModule = pipelineParams.deployImage.replaceAll(".*/", "")
            } else {
                targetModule = pipelineParams.deployImage
                image = pipelineParams.imagePrefix + "/" + pipelineParams.deployImage
            }
            image = image + ":" + pipelineParams.deployTag
            println("[image --> " + image + "]")
            println("[targetModule --> " + targetModule + "]")

            hostName = pipelineParams.deployNamespace + "-" + targetModule + hostNameSuffix

            println("[hostName --> " + hostName + "]")
        }

        tmpYamls += "/" + targetModule
        if (!pipelineParams.delete) {
            stage('2.Deploy to Kubernetes') {

                dir(tmpYamls) {
                    withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                        sh "sed -i 's#{appImage}#$image#g;s#{apolloMeta}#$pipelineParams.deployApolloMeta#g' deployment.yaml"
                        sh "sed -i 's#{apolloNamespace}#${pipelineParams.apolloNamespace}#g' deployment.yaml"
                        sh "sed -i 's#harbor-dev.emkcp.com/devops/skywalking-agent:6.4.0#harbor-dev.emkcp.com/devops/skywalking-agent:6.4.0-fat#g' deployment.yaml"
                        sh "sed -i 's#{hostName}#$hostName#g' ingress.yaml"
                        sh "kubectl -n ${pipelineParams.deployNamespace} apply -f ."
                    }
                }
            }
        } else {
            stage('2.Delete deployment in Kubernetes') {
                dir(tmpYamls) {
                    withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                        sh "sed -i 's#{appImage}#$image#g;s#{apolloMeta}#$pipelineParams.deployApolloMeta#g' deployment.yaml"
                        sh "sed -i 's#{apolloNamespace}#${pipelineParams.apolloNamespace}#g' deployment.yaml"
                        sh "sed -i 's#harbor-dev.emkcp.com/devops/skywalking-agent:6.4.0#harbor-dev.emkcp.com/devops/skywalking-agent:6.4.0-fat#g' deployment.yaml"
                        sh "sed -i 's#{hostName}#$hostName#g' ingress.yaml"
                        sh "cat deployment.yaml"
                        sh "kubectl -n ${pipelineParams.deployNamespace} delete -f ."
                    }
                }
            }
        }
    }
}

return this
