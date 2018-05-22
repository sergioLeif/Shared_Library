package org.site.specific.sharedLibraries

@GrabResolver(name='CENTRAL', root='http://urlPrueba:8085/artifactory/repo1' , m2Compatible=true)
@Grapes(
    @Grab(group='org.jfrog.artifactory.client', module='artifactory-java-client-services', version='0.16')
)

import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClient;
import org.jfrog.artifactory.client.model.Folder;

/**
* Class that implements utilities used by JenkinsFile
*/
class Utilidades implements Serializable {
    
	def steps
	//def envVars = Jenkins.instance.getGlobalNodeProperties()[0].getEnvVars() 
        def envVars = "hello"
	/**
	 * Constructor
	 * 
	 * @param steps
	 */
	Utilidades(steps) {
		this.steps = steps
	}
    
	/**
	 * Execute the SQA´s script to analize code quality
	 * 
	 * @param ARQUITECTURA
	 * @param LENGUAJE
	 * @param UUAA
	 * @param JOB
	 * @param BRANCH_NAME
	 */
    def executeQA(ARQUITECTURA, LENGUAJE, UUAA, JOB, BRANCH_NAME){
        def RSQA = envVars['RUTA_SCRIPT_SQA']
        def ARTIFACTORY_USER = steps.sh "echo ${UUAA}|tr '[A-Z]' '[a-z]'"
        def ARTIFACTORY_PASSWORD = UUAA + 'pass'
        steps.sh "echo ${ARTIFACTORY_PASSWORD}"
        steps.sh "${RSQA}/sonarModularBloqueante.nuevo.sh $ARQUITECTURA $LENGUAJE $ARTIFACTORY_USER $ARTIFACTORY_PASSWORD $UUAA $JOB $BRANCH_NAME"
    }
    
    /**
	 * Inform QA value in to a Deployment issue
	 */
    def String informQA(script, JOB){
        //def BN = script.env.BUILD_NUMBER
        //def String ISSUE = new File('/PATH/scripts/DEVOPS/tmp/'+JOB+BN+'.txt').text.trim()
        //def JN_PADRE = script.env.JOB_NAME.split("/")[0]
        //def BRANCH_NAME = script.env.BRANCH_NAME
        //def RUTA_LOGS = script.env.JENKINS_HOME+'/jobs/'+JN_PADRE+'/branches/'+BRANCH_NAME+'/builds/'+BN+'/log'
        //def RS = envVars['RUTA_SCRIPT_DEVOPS']
        //steps.sh "grep -o 'Codigo:[0-9]' -m1 ${RUTA_LOGS} >> ${script.env.JENKINS_HOME}/scripts/DEVOPS/tmp/${JOB}${BN}_QA.txt"
        
        //def String QA_STATUS = new File('/PATH/scripts/DEVOPS/tmp/'+JOB+BN+'_QA.txt').text.trim()
        
        //steps.sh  "${RS}/updateInformationSQA.sh ${ISSUE} ${QA_STATUS}"
        def String QA_STATUS = 'ok'
        return QA_STATUS
    }
	
	/**
	 * Return number of the releases in a repo.
	 * 
	 * @param UUAA.- Application´s code
	 * @param JOBNAME.- this is the job's name, usually taken from env
	 * @return childrenItemsSize.
	 */
    @NonCPS
    def releaseNumber(UUAA, JOBNAME){
        def REPO = UUAA+"_Artifacts"
        def FOLDER = JOBNAME
        def childrenItemsSize
        
        steps.sh " echo [ReleaseNumber] El valor del repositorio es ${REPO}"
        steps.sh " echo [ReleaseNumber] El valor del repositorio es ${FOLDER}"
        
 		Artifactory artifactory = ArtifactoryClient.create("http://urlPrueba:8083/artifactory/repo1", "user", "passuser")
        try {
            Folder folder = artifactory.repository(REPO).folder(FOLDER).info()
            childrenItemsSize = folder.getChildren().size()
        } catch (Exception e){
            childrenItemsSize = 0
        }
        steps.sh " echo [ReleaseNumber] Tamaño de releases ${childrenItemsSize}"
        return childrenItemsSize
    }
    
	/**
	 * Check if it´s possible deploy a new release.
	 * 
	 * @param UUAA.- Application´s code
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param JOBNAME.- this is the job's name, usually taken from env
	 * @return OK
	 */
    def Boolean validateRelease(UUAA, AUTO_DEPLOY, JOBNAME){
        def releases = releaseNumber(UUAA, JOBNAME)
        steps.sh "echo [validateRelease] Valor de releases ${releases}"
        def OK = true
			if(releases  >= 10 ){
				OK = false
			} 	
		return OK
    }
    
	/**
	 * Download the application´s code in the job´s workspace.
	 * 
	 * @param GIT_REPO_URL.- URL of the git repository that contains the code.
	 * @param JOBNAME.- this is the job's name, usually taken from env
	 * @param BRANCH_NAME.- branch to checkout in the git repository
	 */
    def downLoadSources(GIT_REPO_URL, JOBNAME, BRANCH_NAME, JH){
        //def JH = envVars['JENKINS_HOME']
        def GIT_CREDENTIALS_ID = 'gitlab'
        steps.sh "rm -rf ${JH}/jobs/${JOBNAME}/workspace/*"
        steps.git  branch: BRANCH_NAME, credentialsId: GIT_CREDENTIALS_ID, url: GIT_REPO_URL
        steps.sh "rm -rf ${JH}/workspace/${JOBNAME}*"
    }
    
	/**
	 * Deploy the artifact generated in a Artifactory´s build of the application.
	 * 
	 * @param UUAA.- Application´s code
	 * @param RUTA_BINARIO
	 * @param DEPLOY_TYPE
	 * @param JOB.- Job Jenkins name
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param BRANCH_NAME.- branch to checkout in the git repository
	 * @param JOBNAME.- this is the job's name, usually taken from env. No parsed because artifactory needs it like Jenkins generate it by default.
	 */
	def subidaArtifactory(UUAA, RUTA_BINARIO, DEPLOY_TYPE, JOB, AUTO_DEPLOY, BRANCH_NAME, JOBNAME, CIRCUITO){
        def RS = envVars['RUTA_SCRIPT_DEVOPS']
		if(BRANCH_NAME == "master" || BRANCH_NAME.contains("release") || BRANCH_NAME.contains("hotfix")){
			if(validateRelease(UUAA, AUTO_DEPLOY, JOBNAME)){
				steps.sh " echo Valor de RUTA SCRIPTS ${RS}"
				steps.sh " echo Valor de RUTA SCRIPTS ${RUTA_BINARIO}"
				steps.sh "${RS}/subida_artifactory.sh ${UUAA} ${RUTA_BINARIO} ${DEPLOY_TYPE} ${JOB} ${BRANCH_NAME} ${CIRCUITO}"
			} else{	
				steps.sh "echo Se ha superado el número de releases."
				steps.sh "exit -1"
			}
		}else{
		    steps.sh "${RS}/subida_artifactory.sh ${UUAA} ${RUTA_BINARIO} ${DEPLOY_TYPE} ${JOB} develop ${CIRCUITO}"
		}
    }
	
	/**
	 * Create the JIRA issue relate with the change.
	 * 
	 * @param JOB.- Job Jenkins name
	 * @param LAST_TAG
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param UUAA.- Application´s code
	 * @param JIRA_PROJECT_ID.- Project defined in CA Release Aplication for application.
	 * @param JIRA_AFFECTED_VERSION.
	 * @param BRANCH_NAME.- branch to checkout in the git repository
	 * @param BUILD_NUMBER.- Build version number
	 */
    def String createDeploymentIssue(JOB, LAST_TAG, JIRA_PROJECT_ID, AUTO_DEPLOY, UUAA, DEPLOYMENT_PLAN, JIRA_AFFECTED_VERSION, BRANCH_NAME, BUILD_NUMBER, UPDATE_SENSITIVE_INFORMATION, DEPLOY_TYPE){
		def String ISSUE = null
		if(BRANCH_NAME == "master" || BRANCH_NAME.contains("release") || BRANCH_NAME.contains("hotfix")){
			steps.withCredentials([[$class: 'StringBinding', credentialsId: 'NAME_CREDENTIAL', variable: 'CLAVE']]) {
				def RS = envVars['RUTA_SCRIPT_DEVOPS']
				steps.sh  "${RS}/createDeploymentLinkedIssue.sh ${JOB} ${BUILD_NUMBER} ${LAST_TAG} ${JIRA_PROJECT_ID} ${AUTO_DEPLOY} ${UUAA} ${DEPLOYMENT_PLAN} ${JIRA_AFFECTED_VERSION} ${UPDATE_SENSITIVE_INFORMATION} ${DEPLOY_TYPE}"
				//def File f = new File('/PATH/scripts/DEVOPS/tmp/'+JOB+BUILD_NUMBER+'.txt')
				def String Issue_Key_File = RS + "/tmp/" + JOB + BUILD_NUMBER + ".txt"
				steps.sh " echo Valor de RUTA SCRIPTS ${RS}"
				steps.sh " echo Valor de ISSUE KEY FILE ${Issue_Key_File}"
				def File f = new File(Issue_Key_File)
				ISSUE = f.text.trim()
			}
		} else {
			steps.sh "echo Solo se permite el despliegue desde ramas master, release o hotfix"
			steps.sh "exit -1"
		}
		return ISSUE
    }
	
	/**
	 * Deploy the application
	 * 
	 * @param JOB.- Job Jenkins name
	 * @param BUILD_NUMBER
	 * @param DEPLOYMENT_PLAN.- Project defined in CA Release Aplication for application.
	 * @param UUAA.- Application´s code
	 * @param ENVIRONMENT
	 */
	def deployApplication(JOB, BUILD_NUMBER, CATEGORY, DEPLOYMENT_PLAN, UUAA, ENVIRONMENT){
		steps.withCredentials([[$class: 'StringBinding', credentialsId: 'NAME_CREDENTIAL', variable: 'CLAVE']]) {
		    def Date date = new Date()
		    String FECHA = date.format("HHmmddMM")
            def RS = envVars['RUTA_SCRIPT_DEVOPS']
			steps.sh  "${RS}/deployApplication.sh ${BUILD_NUMBER}_${FECHA} ${JOB}--${BUILD_NUMBER} ${CATEGORY} ${DEPLOYMENT_PLAN} ${UUAA} ${ENVIRONMENT}"
		}
	}
	
	/**
	 * Manage the way to deploy
	 * 
	 * @param ISSUE_KEY
	 * @param UUAA.- Application´s code
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param DEPLOYMENT_TASK.- Condition that indicate if is a deploy with JIRA issue.
	 * @param JOB.- Job Jenkins name
	 * @param BUILD_NUMBER
	 * @param DEPLOYMENT_PLAN.- Project defined in CA Release Aplication for application.
	 * @param ENVIRONMENT
	 */
	def deployApp(ISSUE_KEY, UUAA, DEPLOYMENT_TASK, JOB, BUILD_NUMBER, DEPLOYMENT_PLAN, BRANCH_NAME, ENVIRONMENT, UPDATE_SENSITIVE_INFORMATION){
		try {
				if(DEPLOYMENT_TASK == "YES"){
				    if(UPDATE_SENSITIVE_INFORMATION == "YES"){
				        steps.sh "echo No es posible el Deploy Automático. Es necesario elegir el Responsable del Entorno, posteriormente realizar Deploy de la issue ${ISSUE_KEY}"
				    } else {
				        def RS = envVars['RUTA_SCRIPT_DEVOPS']
					    steps.sh "echo Valor de UUAA: ${UUAA}"
					    steps.sh "${RS}/transitionDeploymentIssue.sh ${ISSUE_KEY} ${UUAA}"
					}
				} else {
					deployApplication(JOB, BUILD_NUMBER, "DEVELOP", DEPLOYMENT_PLAN, UUAA, ENVIRONMENT)
				}
		} catch (err){
			throw err
			sh "exit -1"
		}
	}
	
	/**
	 * Return true when is a release and false in case of snapshot 
	 * 
	 * @param WORKSPACE
	 */
	def Boolean getRelease(WORKSPACE){
	    steps.sh "cat ${WORKSPACE}/pom.xml | grep \"<version>\" | head -1 | cut -d\">\" -f2 | cut -d\"<\" -f1>> moduloprueba.txt"
	    def String fichero = WORKSPACE + "/moduloprueba.txt"
	    def File f = new File(fichero)
        def String cont = f.text.trim()
        steps.sh "rm ${WORKSPACE}/moduloprueba.txt"
        if (cont.toLowerCase().contains("snapshot")){
	        return false
	    }
	    else{return true }
	   
	    
	}
	
	/**
	 * Return true when is a release and false in case of snapshot 
	 * 
	 * @param BRANCH_NAME
	 */
	def Boolean validateBranchName(BRANCH_NAME){
	    if (BRANCH_NAME.count("/") > 1){
			steps.sh "echo El branch tiene mas de un slash"
			return false
		}else{
			String[] array_simbolos = ['+', ',', '&', '(', ')', '@', '#', '$', '=', '?', '¿', '[', ']', '{', '}', '!']
			for(int i=0;i<array_simbolos.size();i++){
				if(BRANCH_NAME.contains(array_simbolos[i])){
					return false
				}
			}
			return true
		}
	}
	
		/**
	 * Return true when is a release and false in case of snapshot 
	 * 
	 * @param GIT_REPO_URL
	 * @param UUAA
	 */
	def Boolean validateUUAA(GIT_REPO_URL, UUAA){
		int slashNumber = GIT_REPO_URL.count("/")
		String group = GIT_REPO_URL.split("/")[slashNumber - 1]
		
		steps.sh "echo El grupo de GitLab es: $group"
		steps.sh "echo La UUAA es: $UUAA"
		
	    if (group == UUAA){
			return true
		}else{
			steps.sh "echo El repositorio fuente el grupo $group no pertenece a la UUAA $UUAA"
			return false
		}
	}
}
