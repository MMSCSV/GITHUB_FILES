
# jenkins-common/build/build.groovy

## Summary
DevOps provided build steps to supplement build pipelines. This file is implemented using Jenkins Shared Libraries [Extending with Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

**Important:** Usage of these tasks will help ease the integration between GitHub, Jenkins, and Artifactory for build promotions.

## Usage
The Devops library loads this file to an object called `Test`.

```
stage('stage-name') {
  Devops.Test.mstest(args)
  Devops.Test.vstest(args)
  Devops.Test.nunit(args)
}
```

### Available Methods
The DevOps build pipeline provides the following methods:

Method | Description
|:---|:---|
```nunit()``` | Executes unit tests with nunit3-console.exe
```mstest()``` | Executes unit tests with Visual Studio 2017 Test Agent MSTest (mstest.exe)
```vstest()``` | Executes unit tests with Visual Studio 2017 Test Agent VSTest (vstest.console.exe)
```setVstestVersion(string Source:Version)``` | Sets VsTest version to be used. Values allowed are: ```"VS2017"``` (Visual Studio 2017 - No version input allowed), ```"VS2019"``` (Visual Studio 2019 - No version input allowed), ```"TestPlatform"``` (Microsoft Test Platform - defaults to version 17.0.0), ```"TestPlatform:17.0.0"``` (Microsoft Test Platform - loads version defined)
```def mapDrive(String IP, String product, String workingDrive, String credentialsVariable = "NOTSENT", String localAdmin = "NOTSENT")``` | Maps a network drive. Specify a ```product``` to use the default product credentials or specify ```credentialsVariable``` and  ```localAdmin``` to use specific credentials.
```psexecLaunch( String IP, String batchFile, String product, String user ="NOTSENT", String credentialsVariable = "NOTSENT", Boolean runAsSystemUser = true)``` | Runs a command in a remote computer. Specify a ```product``` to use the default product credentials or specify ```credentialsVariable``` and  ```user``` to use specific credentials.


Default library pattern: `**/bin/Release/*.Test*.dll` are passed to these methods

#### Test variables
Variable | Description
|:---|:---|
```JKN_VSTEST_EXE``` | Specify the location of vstest.console.exe
```JKN_VSTEST_LOGGER``` | defaults to ```"/Logger:trx"```
```JKN_VSTEST_SETTINGS``` | defaults to empty, but if not empty will be ```"/Settings:<JKN_VSTEST_SETTINGS>"```
```JKN_VSTEST_VERSION``` | defaults to ```"VS2019"```, but will accept versions ```'VS2017'```, ```'VS2019'```, ```'TestPlatform:<version not required - defaults to 17.0.0>'```
```JKN_VSTEST_TESTCASEFILTER``` | defaults to empty, but if not empty will be ```"/TestCaseFilter:<JKN_VSTEST_TESTCASEFILTER>"```
```JKN_MSTEST_EXE``` | Specify the location of mstest.exe
```JKN_MSTEST_SETTINGS``` | MSTest settings
```JKN_MSTEST_RESULTS_DIR``` | MSTest results directory
```JKN_MSTEST_RESULTS_FILE``` | Name of the MSTest results file
```JKN_NUNIT_CONSOLE_EXE``` | Specify the location of nunit3-console.exe
```JKN_NUNIT_CONSOLE_WORK_DIR``` | Working directory for executing nunit3-console.exe
```JKN_NUNIT_CONSOLE_SETTINGS``` | Nunit3-console settings
