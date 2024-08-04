package io.bddevops.jenkins

import io.bddevops.common.Common
import io.bddevops.git.Git

import hudson.tasks.Mailer
import hudson.model.User

class JENKINS implements Serializable {
  def steps
  def Common
  def Git

  def JENKINS_USER = "sa-devops-jenkins"

  JENKINS(steps) {
    this.steps = steps

    Common = new Common(steps)
    Git = new Git(steps)

    steps.GlobalVars.JKN_BUILD_CAUSE = getCauseBuildTrigger()
  }

  // Jenkins api schema available at <jenkins-url>/api/schema, i.e. https://jenkins-pyxis.bddevops.io/api/schema
  //   available data exposed: XML, JSON, Python

  // example: 
  //  def response = callApiJson {
  //     url = "http://jenkins-greg-haag.cfnp.local:8080/job/DevOps/job/jenkins-common/job/PR-152"
  //     schema = "lastSuccessfulBuild"
  //     verbose = false
  //   } 
  def callApiJson( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "url, schema, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }
    
    if( params.find{ it.key == 'url' } == null ) {
      steps.error( "A Jenkins url is required, i.e. ${steps.env.JOB_URL}" )
    }
    def _url = params.url

    if( params.find{ it.key == 'schema' } == null ) {
      steps.error( "A Jenkins schema is required, i.e. build, nextBuildNumber, lastSuccessfulBuild, property, etc.." )
    }
    def _schema = params.schema

    Boolean _verbose = false
    if( params.find{ it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    def response = callApi {
      url = _url
      schema = _schema
      returndata = "json"
      httpmode = "GET"
      verbose = _verbose
    }
    
    def _responseContent = response.content ? response.content : "\"\""

    return "{\"status\":\"${response.status}\",\"content\":${_responseContent}}"
  }

  // example: 
  //  def response = callApi {
  //     url = "http://jenkins-greg-haag.cfnp.local:8080/job/DevOps/job/jenkins-common/job/PR-152"
  //     schema = "lastSuccessfulBuild"
  //     returndata = "json"
  //     httpmode = "GET"
  //     verbose = false
  //   } 
  def callApi( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "url, schema, returndata, httpmode, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }
    
    if( params.find{ it.key == 'url' } == null ) {
      steps.error( "A Jenkins url is required, i.e. ${steps.env.JOB_URL}" )
    }
    def _url = params.url

    if( params.find{ it.key == 'schema' } == null ) {
      steps.error( "A Jenkins schema is required, i.e. build, nextBuildNumber, lastSuccessfulBuild, property, etc.." )
    }
    def _schema = params.schema

    def _returnData = "json"
    if( params.find{ it.key == 'returndata' } != null ) {
      _returnData = params.returndata
    }
    if( !"json,xml,python".contains(_returnData) ) { steps.error( "The Jenkins API only supports the following return data: json,xml,python" ) }
    
    def _httpmode = "GET"
    if( params.find{ it.key == 'httpmode' } != null ) {
      _httpmode = params.httpmode
    }

    Boolean _verbose = false
    if( params.find{ it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }
    
    def _requesturl = "${_url}/${_schema}/api/${_returnData}"

    def response = sendRequest( _requesturl, _httpmode )

    if( _verbose ) { steps.println response }

    return response
  }

  def sendRequest( String _requesturl, String _httpmode = "GET") {
    def _remoteJenkins = _requesturl.split("job")[0]
    def crumbData = getCrumb(_remoteJenkins)
    def auth = getBasicAuth()

    def response = steps.httpRequest (
          url: _requesturl, 
          customHeaders: [[name: 'Jenkins-Crumb', value: "${crumbData['crumb']}"],
                          [name: 'Authorization', value: "Basic ${auth}"],
                          [name:'Cookie', value: "${crumbData['cookieContent']}"]],
          httpMode: _httpmode,
          validResponseCodes: '100:500',
          timeout: 60
    )

    return response
  }

  def getUserEmailFromJenkinsMailer( String userId ) {
    User u = User.get( userId )
    def _userEmail = u.getProperty(Mailer.UserProperty.class)
    return _userEmail.getAddress()
  }

  def getJenkinsXML(String jenkinsJob, String build, String server = "http://jenkins-swet.cfnp.local:8080", Boolean verbose = false) {
    def requestUrl = "${server}/${jenkinsJob}/${build}/testReport/api/xml"
    def crumbData = getCrumb(jenkinsUrl)
    def auth = getBasicAuth()
    steps.println "Getting Test XML"
    def request = steps.httpRequest (url: requestUrl, 
                              customHeaders: [[name: 'Jenkins-Crumb', value: "${crumbData['crumb']}"],
                                              [name: 'Authorization', value: "Basic ${auth}"],
                                              [name:'Cookie', value: "${crumbData['cookieContent']}"]],
                              httpMode: 'POST',
                              validResponseCodes: '100:500')
    
    if (request.status != 200)
    {
      steps.println "Bad Response: ${request.status}"        
    }
    if (verbose) { steps.println request.content }
    return request.content
  }


  def triggerRemoteJenkins(String jobJson) {
    def props = steps.readJSON text: jobJson
    def remoteJenkins = "${props['remoteJenkins']}"
    def jenkinsJob = "${props['jenkinsJob']}"
    def remoteJob = "${remoteJenkins}/${jenkinsJob}"
    def buildUrlTag = "/build"
    
    def crumbData = getCrumb(remoteJenkins)
    def auth = getBasicAuth()

    def urlParams = buildParams("${jobJson}")
    if (urlParams) { buildUrlTag = "/buildWithParameters" }

    def requestUrl = "${remoteJob}${buildUrlTag}${urlParams}"
    
    steps.println "Triggering Remote Job: ${requestUrl}"
    def request = steps.httpRequest (url: requestUrl, 
                              customHeaders: [[name: 'Jenkins-Crumb', value: "${crumbData['crumb']}"],
                                              [name: 'Authorization', value: "Basic ${auth}"],
                                              [name:'Cookie', value: "${crumbData['cookieContent']}"]],
                              httpMode: 'POST',
                              validResponseCodes: '100:500') 
    if (request.status != 201)
    {
      steps.println "Build Not Started"
      steps.println request.content
    }
  }

  def triggerRemoteJenkins( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "url, parameters"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    if( params.find{ it.key == 'url' } == null ) {
      steps.error( "A Jenkins remote url is required, i.e. ${steps.env.JOB_URL}" )
    }
    def _url = params.url

    // params are in the format: "key1=value1&key2=value2&key3=value3"
    def _params = ""
    if( params.find{ it.key == 'parameters' } != null ) {
      _params = params.params
    }
    def _schema = _params ? "/buildWithParameters?${_params}" : "/build"

    def _remoteUrl = "${_url}${_schema}"

    def response = sendRequest( _remoteUrl, "POST" )
    if (response.status != 201)
    {
      steps.println "Build Not Started."
      steps.println response.content
    }
  }

  def getJenkinsConsoleText(String outFile = "jenkins-console-log.txt", String buildUrl = steps.env.BUILD_URL, String jenkinsUrl = steps.env.JENKINS_URL) {
    def requestUrl = "${buildUrl}consoleText"
    def crumbData = getCrumb(jenkinsUrl)
    def auth = getBasicAuth()
    def request = steps.httpRequest (url: requestUrl, 
                              outputFile: outFile, 
                              customHeaders: [[name: 'Jenkins-Crumb', value: "${crumbData['crumb']}"],
                                              [name: 'Authorization', value: "Basic ${auth}"],
                                              [name:'Cookie', value: "${crumbData['cookieContent']}"]],
                              httpMode: 'POST',
                              validResponseCodes: '100:500')
    def _return = request.content
    if (request.status != 200)
    {
      steps.println "THERE WAS AN ISSUE RETRIEVING THE JENKINS BUILD LOG. REQUEST STATUS: " + request.status
      _return = ""
    }
    return _return
  }

  def getLastSuccessfulBuild( String jenkinsJoburl ) {
    def _buildId = ""
    def response = steps.readJSON text: callApiJson {
      url = jenkinsJoburl
      schema = "lastSuccessfulBuild"
    }
    _buildId = response.content.toString().contains('id') ? response.content['id'] : null
    if( _buildId == null ) { steps.println "No lastSuccessfulBuild found at job: ${jenkinsJoburl}" }
    return _buildId
  }

  def getLastSuccessfulBuildCommit( String jenkinsJoburl ) {
    def _buildId = ""
    def response = steps.readJSON text: callApiJson {
      url = jenkinsJoburl
      schema = "lastSuccessfulBuild"
    }
    _buildId = response.content.toString().contains('changeSets') ? response.content.changeSets.items.last().commitId[0] : null
    if( _buildId == null ) { steps.println "No lastSuccessfulBuild commit found at job: ${jenkinsJoburl}" }

    return _buildId
  }

  def getLastGoodBuild(String jobJson) {
    def props = steps.readJSON text: jobJson
    def remoteJenkins = "${props['remoteJenkins']}"
    def jenkinsJob = "${props['jenkinsJob']}"
    def remoteJob = "${remoteJenkins}/${jenkinsJob}"
    def urlParams = buildParams("${jobJson}")
    def crumbData = getCrumb(remoteJenkins)
    def auth = getBasicAuth()
    def requestUrl = "${remoteJob}/lastSuccessfulBuild/api/json${urlParams}"

    steps.println "Get Last known good Build"
    def request = steps.httpRequest (url: requestUrl, 
                              customHeaders: [[name: 'Jenkins-Crumb', value: "${crumbData['crumb']}"],
                                              [name: 'Authorization', value: "Basic ${auth}"],
                                              [name:'Cookie', value: "${crumbData['cookieContent']}"]],
                              httpMode: 'GET',
                              validResponseCodes: '100:500')
    
    def idValues = steps.readJSON text: request.content
    def buildId = "${idValues['id']}"
    steps.println buildId
    if (request.status != 200)
    {
      steps.println "Get Failed";
      steps.println request.content;
    }
    else {
      steps.println "Return ID";
      return buildId
    }
  }

  /* retrieves jenkins crumb and session id */
  private def getCrumb(String remoteJenkins){
    def auth = getBasicAuth()
    steps.println "Getting Jenkins Crumb Data"
    def response = steps.httpRequest (url: remoteJenkins + "/crumbIssuer/api/json/", 
                                customHeaders: [[name: 'Authorization', value: "Basic ${auth}"]], 
                                httpMode: 'GET',
                                validResponseCodes: '100:500')
    
    if (response.status == 403 || response.status == 401)
    {
      steps.println "Authentication Failed For Crumb:"
      steps.println response.content
    }

    // retrieve session id from request.
    def cookieContent = response.headers.get("Set-Cookie")[0].tokenize(';')[0]

    // create crumb data
    def crumbData = steps.readJSON text: response.content
    crumbData['cookieContent'] = cookieContent
    
    return crumbData;
  }

  private def getBasicAuth(String _credentials = JENKINS_USER) {
    def auth
    steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: _credentials, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
      steps.println "Getting Basic Auth"
      if(!Boolean.valueOf(steps.isUnix())) {
          auth = steps.powershell(returnStdout: true, script: '''[Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$ENV:USERNAME`:$ENV:PASSWORD"))''')
      }
      else {
          auth = Common.genericScriptCmdReturn("printf ${steps.USERNAME}:${steps.PASSWORD} | base64")
      }
    }
    return auth
  }

  def buildParams(String jobJson){
    def props = steps.readJSON text: jobJson
    def urlParams = ""
    def params = ""
    if (props.keySet().contains( 'parameters' )) {
      try
      {
        params = props.parameters
        int count = 0
        params.each{
          key, value -> if (count == 0) urlParams = '?' + key + '=' + value else urlParams = urlParams +'&' + key + '=' + value
          count ++
        }
      }
      catch(e)
      {
        steps.println "exception: " + e
        urlParams = ""
      }
    }
    return urlParams;
  }

  def getBuildCause() {
    return steps.GlobalVars.JKN_BUILD_CAUSE
  }

  def getBuildCauseUser() {
    return steps.GlobalVars.JKN_BUILD_CAUSE.user
  }

  def getCauseUser() {
    return getBuildCauseUser()
  }

  def getBuildCauseUserEmail() {
    def _user = ""
    if( steps.GlobalVars.JKN_BUILD_CAUSE.id ) {
      _user = getUserEmailFromJenkinsMailer( steps.GlobalVars.JKN_BUILD_CAUSE.id )
    } 
    return _user
  }

  def getBuildCauseTrigger() {
    return steps.GlobalVars.JKN_BUILD_CAUSE.trigger
  }

  def getBuildCauseDescription() {
    return steps.GlobalVars.JKN_BUILD_CAUSE.description
  }

  @NonCPS
  private def getCauseBuildTrigger() {
    def causesMap = [ "user": "", "id": "", "trigger": "", "description": "" ]

    def _causes = steps.currentBuild.buildCauses
    _causes.each {
      if ( it._class == 'hudson.model.Cause\$UserIdCause' ) {
        causesMap.user = it.userName.toString()
        causesMap.id = it.userId.toString()
        causesMap.trigger = "userid"
        causesMap.description = it.shortDescription.toString()
      } 
      if ( it._class == 'hudson.triggers.TimerTrigger\$TimerTriggerCause' ) {
        causesMap.trigger = "timertrigger"
        causesMap.description = it.shortDescription.toString()
      }
      if ( it._class == 'org.jenkinsci.plugins.workflow.cps.replay.ReplayCause' ) {
        causesMap.trigger = "replay"
        causesMap.description = it.shortDescription.toString()
      }
      if ( it._class == 'jenkins.branch.BranchEventCause' ) {
        causesMap.trigger = "branchevent"
        causesMap.description = it.shortDescription.toString()
      }
      if ( it._class == 'org.jenkinsci.plugins.workflow.support.steps.build.BuildUpstreamCause' ) {
        causesMap.trigger = "buildupstream"
        causesMap.description = it.shortDescription.toString()
      }
    }
    _causes = null
    return causesMap
  }

}
