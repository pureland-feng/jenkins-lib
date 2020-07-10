import com.cloudbees.plugins.credentials.CredentialsNameProvider
import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.FileCredentials

def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
);

for (credential in creds) {

    if(credential instanceof StandardUsernamePasswordCredentials){
        println("""
                Username Password
                ID: ${credential.id}
                Description: ${credential.description}
                Username: ${credential.username}
                Password: ${credential.password?.getPlainText()}\n\n
            """)

    }else if(credential instanceof SSHUserPrivateKey){

        println("""
                SSH User Private Key
                ID: ${credential.id}
                Description: ${credential.description}
                Key:
                ${credential.privateKeySource?.getPrivateKey()}
                Passphrase:
                ${credential.passphrase?.getPlainText()}\n\n
            """)

    }else if(credential instanceof StringCredentials){

        println("""
                String
                ID: ${credential.id}
                Description: ${credential.description}
                Secret: ${credential.secret?.getPlainText()}\n\n
            """)

    }else if(credential instanceof FileCredentials){

        println("""
                File
                ID: ${credential.id}
                Description: ${credential.description}
                Content:
                ${credential.content?.text}\n\n
            """)

    }else{

        println("""
                ${credential.class.name}
                ID: ${credential.id}
                Description: ${credential.description}\n\n
            """)
    }

}