package io.bddevops.test
//   supports: mstest, vstest, nunit

import io.bddevops.common.Common
import io.bddevops.nuget.Nuget

class Test implements Serializable {
  def steps
  def Common
  def Nuget 

  def SWET_ES_CREDENTIALS = "ESAdmin"
  def SWET_NEO_CREDENTIALS = "NeoAdmin"
  def SWET_ESSTN_CREDENTIALS = "CfnAdmin"
  def SWET_SM_CREDENTIALS = "AlarisAdmin"
  def SWET_PLX_CREDENTIALS = "administrator"
  def SWET_ROWA_CREDENTIALS = "BDLocalAdmin"
  def SWET_CCE_CREDENTIALS = "BDLocalAdmin"
  def userId=null
  def TESTRESULTS_FOLDER = "TestResults"

  Test(steps) {
    this.steps = steps

    Common = new Common(steps)
    Nuget = new Nuget(steps)
  }

  def execute(String programExe, String args) {
    Common.genericScriptCmd("${programExe} ${args}")
  }

  def mstest(List<String> testDll) {
    mstest(testDll.join(","))
  }

  def getMstestExe() {
    return steps.GlobalVars.JKN_MSTEST_EXE
  }

  def setMstestExe(String props) {
    steps.GlobalVars.JKN_MSTEST_EXE = props
  }

  def getMstestSettings() {
    return steps.GlobalVars.JKN_MSTEST_SETTINGS
  }

  def setMstestSettings(String props) {
    steps.GlobalVars.JKN_MSTEST_SETTINGS = props
  }

  def getMstestResultsDir() {
    return steps.GlobalVars.JKN_MSTEST_RESULTS_DIR
  }

  def setMstestResultsDir(String props) {
    steps.GlobalVars.JKN_MSTEST_RESULTS_DIR = props
  }

  def mstest(String testDll = "**/bin/Release/*.Test*.dll") {
    def mstestExe = steps.GlobalVars.JKN_MSTEST_EXE ? steps.GlobalVars.JKN_MSTEST_EXE : "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\Common7\\IDE\\MSTest.exe"
    def mstestSettings = steps.GlobalVars.JKN_MSTEST_SETTINGS ? steps.GlobalVars.JKN_MSTEST_SETTINGS : ""
    def mstestResultsDir = steps.GlobalVars.JKN_MSTEST_RESULTS_DIR ? steps.GlobalVars.JKN_MSTEST_RESULTS_DIR : TESTRESULTS_FOLDER
    def mstestResultsFile = steps.GlobalVars.JKN_MSTEST_RESULTS_FILE ? steps.GlobalVars.JKN_MSTEST_RESULTS_FILE : "results-${steps.GlobalVars.JKN_RANDOM.nextInt()}.trx"

    if (mstestResultsDir) {
      steps.dir(mstestResultsDir) {
        steps.deleteDir()
      }
      Common.genericScriptCmd("mkdir ${mstestResultsDir}")
      mstestResultsFile = "${mstestResultsDir}\\${mstestResultsFile}"
    }

    def mstestDlls = ""
    def mstestArgs = "/nologo /usestderr"
    if (mstestSettings) {
      mstestArgs = "${mstestArgs} /testsettings:${mstestSettings}"
    }
    if (mstestResultsFile) {
      mstestArgs = "${mstestArgs} /resultsfile:${mstestResultsFile}"
    }

    if( testDll.contains("\\") ) { 
      testDll = testDll.replace( "\\", "/")
    }
    def files = steps.findFiles(glob: testDll)
    if( files.size() > 0 ) {
      for( def file in files ) {
        steps.println "Adding Test File: ${file}"
        mstestDlls = "${mstestDlls} \"/testcontainer:${file}\""
      }
    }
    if( mstestDlls ) {
      mstestArgs = "${mstestArgs} ${mstestDlls}"
      steps.println """
============================================================
MSTest Command: ${mstestExe}
MSTest Args: ${mstestArgs}
============================================================
"""
      try {
        Common.genericScriptCmd("\"${mstestExe}\" ${mstestArgs}")
      }
      catch (e) {
        steps.mstest failOnError: false, testResultsFile: mstestResultsFile
        steps.error("Unit Tests Failed. \n${e}")
      }
    } else {
      steps.println """
        No *.Tests.dll found for glob: ${testDll}
      """
    }
    if (!steps.env.POD_LABEL) {
      steps.mstest failOnError: false, testResultsFile: mstestResultsFile
    }
    Common.buildresultsStash("mstestTestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}","**/TestResults/**/*")
  }

  /* **********************************************
    nunit.console.exe
  */
  def nunit(List<String> testDll) {
    nunit(testDll.join(","))
  }

  def nunit(String testDll = "**/bin/Release/*.Test*.dll") {
    // the chocolatey package installs nunit3-console.exe into the chocolatey bin filepath
    def nunitConsoleResultsFilename = "TestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}.xml"
    def nunitConsoleExe = steps.GlobalVars.JKN_NUNIT_CONSOLE_EXE ? steps.GlobalVars.JKN_NUNIT_CONSOLE_EXE : "C:\\ProgramData\\chocolatey\\bin\\nunit3-console.exe"
    def nunitConsoleSettings = steps.GlobalVars.JKN_NUNIT_CONSOLE_SETTINGS ? steps.GlobalVars.JKN_NUNIT_CONSOLE_SETTINGS : ""
    def nunitConsoleWorkDir = steps.GlobalVars.JKN_NUNIT_CONSOLE_WORK_DIR ? steps.GlobalVars.JKN_NUNIT_CONSOLE_WORK_DIR : TESTRESULTS_FOLDER
    def nunitConsoleDlls = ""
    def nunitConsoleArgs = "--work ${nunitConsoleWorkDir}"
    if (nunitConsoleSettings) {
      nunitConsoleArgs = "${nunitConsoleArgs} ${nunitConsoleSettings}"
    }

    if( testDll.contains("\\") ) { 
      testDll = testDll.replace( "\\", "/")
    }
    def files = steps.findFiles(glob: testDll)
    if( files.size() > 0 ) {
      for( def file in files ) {
        steps.println "Adding Test File: ${file}"
        nunitConsoleDlls = "${nunitConsoleDlls} \"${file}\""
      }
    }
    if( nunitConsoleDlls ) {
      nunitConsoleArgs = "${nunitConsoleArgs} --result ${nunitConsoleResultsFilename} ${nunitConsoleDlls}"
      steps.println """
============================================================
NUnit Console: ${nunitConsoleExe}
NUnit Console Args: ${nunitConsoleArgs}
============================================================
"""
      try {
        Common.genericScriptCmd("\"${nunitConsoleExe}\" ${nunitConsoleArgs}")
      }
      catch (e) {
        steps.nunit failOnError: false, testResultsPattern: "${nunitConsoleWorkDir}\\${nunitConsoleResultsFilename}"
        steps.error("Unit Tests Failed. \n${e}")
      }
    } else {
      steps.println """
        No *.Tests.dll found for glob: ${testDll}
      """
    }
    if (!steps.env.POD_LABEL) {
      steps.nunit failIfNoResults: false, testResultsPattern: "${nunitConsoleWorkDir}\\${nunitConsoleResultsFilename}"
    }
    Common.buildresultsStash("nunitTestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}","**/TestResults/**/*")
  }

  def getNunitConsoleExe() {
    return steps.GlobalVars.JKN_NUNIT_CONSOLE_EXE
  }

  def setNunitConsoleExe(String props) {
    steps.GlobalVars.JKN_NUNIT_CONSOLE_EXE = props
  }

  def getNunitConsoleSettings() {
    return steps.GlobalVars.JKN_NUNIT_CONSOLE_SETTINGS
  }

  def setNunitConsoleSettings(String props) {
    steps.GlobalVars.JKN_NUNIT_CONSOLE_SETTINGS = props
  }

  def getNunitConsoleWorkDir() {
    return steps.GlobalVars.JKN_NUNIT_CONSOLE_WORK_DIR
  }

  def setNunitConsoleWorkDir(String props) {
    steps.GlobalVars.JKN_NUNIT_CONSOLE_WORK_DIR = props
  }

  /* **********************************************
    vstest.console.exe
  */
  def vstest( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "files, exe, logger, resultsdir, settingsfile, testcasefilter, properties, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }
    def testResultsFile = "TestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}.trx"
    def testResultsFilePath = "${TESTRESULTS_FOLDER}\\${testResultsFile}"

    def _files = "**/bin/Release/*.Test*.dll"
    if( params.find { it.key == 'files' } != null ) {
      _files = params.files
    }
    def _exe = getVstestExe()
    if( params.find { it.key == 'exe' } != null ) {
      _exe = params.exe
    }
    def _logger = "/Logger:trx;LogFileName=${testResultsFile}"
    if( params.find { it.key == 'logger' } != null ) {
      _logger = params.logger
    }
    def _resultsdir = TESTRESULTS_FOLDER
    if( params.find { it.key == 'resultsdir' } != null ) {
      _resultsdir = params.resultsdir
    }
    def _settingsfile = ""
    if( params.find { it.key == 'settingsfile' } != null ) {
      _settingsfile = params.settingsfile
    }
    def _testcasefilter = ""
    if( params.find { it.key == 'testcasefilter' } != null ) {
      _testcasefilter = params.testcasefilter
    }
    def _properties = ""
    if( params.find { it.key == 'properties' } != null ) {
      _properties = params.properties
    }
    Boolean _verbose = false
    if( params.find{ it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    setVstestExe( _exe )
    setVstestLogger( _logger )
    setVstestLoggerResultsDir( _resultsdir )
    setVstestSettings( _settingsfile )
    setVstestTestCaseFilter( _testcasefilter )
    setVstestProperties( _properties )
    vstest( _files, _verbose )
  }

  def vstest(List<String> testDll, Boolean verbose = false) {
    steps.vstest( testDll.join(","), verbose )
  }

  def vstest(String testDll = "**/bin/Release/*.Test*.dll", Boolean verbose = false) {
    def testResultsFile = "TestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}.trx"
    def testResultsFilePath = "${TESTRESULTS_FOLDER}\\${testResultsFile}"

    def vstestExe = getVstestExe()
    def vstestLogger = getVstestLogger() ? getVstestLogger() : "/Logger:trx;LogFileName=${testResultsFile}"
    def vstestLoggerResultsDir = getVstestLoggerResultsDir() ? "/ResultsDirectory:${getVstestLoggerResultsDir()}" : "/ResultsDirectory:${TESTRESULTS_FOLDER}"
    def vstestSettings = getVstestSettings() ? getVstestSettings() : ""
    def vstestCaseFilter = getVstestTestCaseFilter() ? getVstestTestCaseFilter() : ""
    def vstestProperties = getVstestProperties() ? getVstestProperties() : ""
    def vstestDlls = ""
    def vstestArgs = "${vstestLogger} ${vstestLoggerResultsDir} /InIsolation"
    vstestArgs = vstestSettings ? "${vstestArgs} ${vstestSettings}": vstestArgs
    vstestArgs = vstestCaseFilter ? "${vstestArgs} ${vstestCaseFilter}": vstestArgs
    vstestArgs = vstestProperties ? "${vstestArgs} ${vstestProperties}": vstestArgs

    if( testDll.contains("\\") ) { 
      testDll = testDll.replace( "\\", "/")
    }
    def files = steps.findFiles(glob: testDll)
    if( files.size() > 0 ) {
      for( def file in files ) {
        if( verbose ) { steps.println "Adding Test File: ${file}" }
        vstestDlls = "${vstestDlls} \"${file}\""
      }
    }
    if( vstestDlls ) {
      vstestArgs = "${vstestArgs} ${vstestDlls}"
      steps.println """
============================================================
VsTest Command: ${vstestExe}
VsTest Args: ${vstestArgs}
============================================================
"""
      try {
        Common.genericScriptCmd("\"${vstestExe}\" ${vstestArgs}")
      }
      catch (e) {
        def _resultsfile = steps.findFiles( glob: "${getVstestLoggerResultsDir()}/*.trx" )
        if( _resultsfile.size() > 0 ) {
          steps.mstest failOnError: false, testResultsFile: _resultsfile[0].path
        }
        steps.error("Unit Tests Failed. \n${e}")
      }
    } else {
      steps.println """
        No Test DLLs found for glob: ${testDll}
      """
    }
    if (!steps.env.POD_LABEL) {
      def _resultsfile = steps.findFiles( glob: "${getVstestLoggerResultsDir()}/*.trx" )
      if( _resultsfile.size() > 0 ) {
        steps.mstest failOnError: false, testResultsFile: _resultsfile[0].path
      }
    }
    Common.buildresultsStash("vstestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}","**/TestResults/**/*")
  }

  def setVsTestVersion(String version = "VS2019") {
    def vstestPath=""
    def ver = version.toLowerCase()
    def testPlatformVersion="17.0.0"
    if (ver.startsWith("testplatform")){
      if(ver.contains(":")){
        testPlatformVersion= ver.tokenize(':')[1]
        ver = ver.tokenize(':')[0]
      }
    }

    switch(ver){
        case "vs2017":
          vstestPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\TestAgent\\Common7\\IDE\\CommonExtensions\\Microsoft\\TestWindow\\vstest.console.exe"
          break
        case "vs2019":
          vstestPath = "C:\\program files (x86)\\microsoft visual studio\\2019\\buildtools\\common7\\ide\\commonextensions\\microsoft\\testwindow\\vstest.console.exe"          
          break
        case "testplatform":
          String targetDir = "${steps.GlobalVars.JKN_WORKSPACE}/vstestPackage"
          targetDir = targetDir.replace('\\','/')
          Nuget.execute("install Microsoft.TestPlatform -Version ${testPlatformVersion} -OutputDirectory ${targetDir}")
          def files = steps.findFiles(glob: "**/vstest.console.exe")
          def targetPath = files[0].path.replace('/','\\')
          vstestPath = targetPath
          if (vstestPath == "") {
            steps.error ("Unable to install and find MicrosoftTestPlatform")
          }
          break
        default:
          steps.error("Please input one of the following options:\n'VS2017' - Visual Studio 2017 \n'VS2019' - Visual Studio 2019 \n'testplatform:<version>' - MS Test Platform 17.0.0")          
          break
    }
    steps.println "vstestPath=${vstestPath}"
    if (vstestPath!=""){                          // If VsTestPath is not empty, then
      setVstestExe( vstestPath )
   }
  }

  def getVstestExe() {
    steps.GlobalVars.JKN_VSTEST_EXE ? steps.GlobalVars.JKN_VSTEST_EXE : setVsTestVersion()
    return steps.GlobalVars.JKN_VSTEST_EXE
  }

  def setVstestExe(String props) {
    steps.GlobalVars.JKN_VSTEST_EXE = props
  }

  def getVstestLogger() {
    return steps.GlobalVars.JKN_VSTEST_LOGGER
  }

  def setVstestLogger(String props) {
    def _props = props
    if( !props.startsWith("/Logger:") && props ) {
      _props = "/Logger:${props}"
    }
    steps.GlobalVars.JKN_VSTEST_LOGGER = _props
  }

  def getVstestLoggerResultsDir() {
    return TESTRESULTS_FOLDER
  }

  def setVstestLoggerResultsDir( String dir = "TestResults" ) {
    TESTRESULTS_FOLDER = dir
  }

  def setVstestLoggerTypes( String logger = "trx", String logname = "" ) {
    def _logger = logger.toLowerCase().split(',')
    def _loggers = ""
    def _logfilename = logname ? "LogFileName=${logname}" : "LogFileName=TestResults-${steps.GlobalVars.JKN_RANDOM.nextInt()}"

    // check supported logger types
    _logger.each {
      if( !"trx,html".contains(it) ) {
        steps.error("Unsupported logger: ${it} \nSupported loggers: trx,html. ")
      }
    }

    _logger.each {
      _loggers = _loggers ? "${_loggers} /Logger:${it};${_logfilename}.${it}" : "/Logger:${it};${_logfilename}.${it}"
    }

    setVstestLogger( _loggers )
  }

  def getVstestSettings() {
    return steps.GlobalVars.JKN_VSTEST_SETTINGS
  }

  def setVstestSettings(String props) {
    //The /Settings parameter requires a settings file to be provided.
    def _props = props
    if( !props.startsWith("/Settings:") && props ) {
      _props = "/Settings:${props}"
    }
    steps.GlobalVars.JKN_VSTEST_SETTINGS = _props
  }

  def getVstestTestCaseFilter() {
    return steps.GlobalVars.JKN_VSTEST_TESTCASEFILTER
  }

  def setVstestTestCaseFilter(String props) {
    //Requires: Filter value can be <property>=<value> type. Examples: "Priority=1", "TestCategory=Nightly"
    def _props = props
    if( !props.startsWith("/TestCaseFilter:") && props ) {
      _props = "/TestCaseFilter:${props}"
    }
    steps.GlobalVars.JKN_VSTEST_TESTCASEFILTER = _props
  }

  def getVstestProperties() {
    return steps.GlobalVars.JKN_VSTEST_PROPERTIES
  }

  def setVstestProperties(String props) {
    steps.GlobalVars.JKN_VSTEST_PROPERTIES = props
  }

  def addVstestProperties(String props) {
    steps.GlobalVars.JKN_VSTEST_PROPERTIES = props
  }

  // Run commands in remote machine using psexec
  // user: if empty or 'NOTSENT' credentials are pulled from product credentials
  //       if user is passed in then the credentialsVariable is the user password 
  def psexecLaunch( String IP, 
                    String batchFile, 
                    String product,
                    String user ="NOTSENT",
                    String credentialsVariable = "NOTSENT", 
                    Boolean runAsSystemUser = true) {
    
    // resolve user account 
    def userId = getCredentials(product, user)
          
    // TODO: not wrapping with 'withCredentials' block result in displaying the secret value
    if(credentialsVariable != '' && credentialsVariable != 'NOTSENT')
      return psexecLaunchCommand(IP, batchFile, userId, credentialsVariable, runAsSystemUser)

    // run psexec with product credentials
    steps.withCredentials([steps.string(credentialsId: userId, variable: 'TOKEN')])
    {
      return psexecLaunchCommand(IP, batchFile, userId, steps.env.TOKEN, runAsSystemUser)      
    }
  }

  // Runs a remote command using psexec
  // TODO: not wrapping with 'withCredentials' block result in displaying the secret value
  private def psexecLaunchCommand( String IP,
                    String batchFile,
                    String userId,
                    tokenVar,
                    Boolean runAsSystemUser = true) 
  {
    def systemUserArg = runAsSystemUser ? "-s" : ""
    steps.bat(script: """psexec.exe \\\\${IP} -accepteula -u ${userId} -p ${tokenVar} -i 1 ${systemUserArg} -d cmd.exe /c ${batchFile}""", returnStatus: true)   
  }

  // Map a network drive
  // localAdmin: if empty or 'NOTSENT' credentials are pulled from product credentials
  //             if user is passed in then the credentialsVariable is the user password
  def mapDrive(String IP,
              String product,
              String workingDrive,
              String credentialsVariable = "NOTSENT",
              String localAdmin = "NOTSENT")
  { 
    // resolve user account
    def credentials = getCredentials(product, localAdmin)
    
    // map drive with specified user and password
    // TODO: not wrapping with  'withCredentials' block result in displaying the secret value
    if(credentialsVariable != '' && credentialsVariable != 'NOTSENT')
        return mapDriveCommand(IP, workingDrive, credentials, credentialsVariable)

    // map drive with product credentials
    steps.withCredentials([steps.string(credentialsId: credentials, variable: 'TOKEN')])
    {
      return mapDriveCommand(IP, workingDrive, credentials, steps.env.TOKEN)
    }
  }

  def cleanDrives(){
    steps.echo "Clean Drives"
    def drivesFound = steps.bat(script: """net use""", returnStdout: true )
    def stdOut = ""
    def driveUse = ""
    steps.echo "Drives Found: ${drivesFound}"
    def alphaRange = 'G'..'Z'
    for (drive in alphaRange)
    {   
      try{
        steps.println "command: net use ${drive}: /delete" 
        stdOut = steps.bat(script: """net use ${drive}: /delete""", returnStdout: true)
        steps.println "output: ${stdOut}"
      }
      catch (e){
        steps.println "Failure: ${e}"
      }
    }
    steps.println("Drives remaining:")
    steps.bat(script: """net use""", returnStdout: true )
  }

  // Map a network drive
  // tokenVar: pass environment variable if wrapped with 'withCredentials' block
  //           pass string if coming from plain password
  private def mapDriveCommand(String IP, String workingDrive, String userId, tokenVar)
  {
    def stdOut = ""
    def path = "\\\\" + IP + "\\" + workingDrive + "\$" 

    def command = "net use " + path + " ${tokenVar} /persistent:no /user:${userId}"

    steps.echo command
    stdOut = steps.bat(script: """${command}""", returnStdout: true)
    steps.println "Path: ${path}"
    return path
  }

  // Gets the credentials based on product or user account.
  // - If 'userAccount' is empty or 'NOTSENT' then credentials are pulled from the product
  private def getCredentials(String product, String userAccount = "NOTSENT")
  {
    // use account from parameter    
    if(userAccount != '' && userAccount != 'NOTSENT')
        return userAccount

    // use configured account for product
    def userId = ''
    switch(product)
    {
      case 'neo':
        userId = SWET_NEO_CREDENTIALS
        break
      case 'es':
        userId = SWET_ES_CREDENTIALS
        break
      case 'esstn':
        userId = SWET_ESSTN_CREDENTIALS
        break
      case 'sm':
        userId = SWET_SM_CREDENTIALS
        break
      case 'rowa':
        userId = SWET_ROWA_CREDENTIALS
        break
      case 'cce':
        userId = SWET_CCE_CREDENTIALS
        break
      case 'rowawkstn':
        userId = "CfnAdmin"
        break
      case 'plx':
        userId = SWET_PLX_CREDENTIALS
        break
      default:
        userId = SWET_NEO_CREDENTIALS
        break
    }

    return userId
  }
}
