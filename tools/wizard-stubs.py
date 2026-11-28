#!/usr/bin/env python3
"""Stand-ins for Bitbucket, Jenkins and OIS to try the guided pipeline set-up of ITSS COE OneView on a workstation.

They answer just the calls OneView makes and keep the jobs it creates in memory:

  Bitbucket Server  http://localhost:<port>/bitbucket   repository /projects/CERT/repos/certscanner-api
  Jenkins           http://localhost:<port>/jenkins     existing job /job/CERT/job/certscanner-api/ with build 42
  OIS               https://localhost:<port + 1>        with --ois-cert, --ois-key and --ois-client-ca

Bitbucket and Jenkins accept only the generic user 'svc-oneview' with the secret the OIS stand-in gives out.
Usage: tools/wizard-stubs.py [--port 8099] [--ois-cert cert.pem --ois-key key.pem --ois-client-ca ca.pem]
"""
import argparse
import base64
import json
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

USER, SECRET, API_KEY = 'svc-oneview', 'secret-from-ois', 'stub-a2a-api-key'

SOURCES = {
    'Jenkinsfile': "@Library('DevSecOpsJenkinsLibrary') _\n\n"
                   "devSecOpsSecurityPipeline(projectNames: 'certscanner-api', agentNames: ['linux-agent-17'])\n",
    'build.gradle': "plugins { id 'java'\n id 'jacoco' }\n"
                    "java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }\n",
    'settings.gradle': "rootProject.name = 'certscanner-api'\n",
    'gradle/wrapper/gradle-wrapper.properties':
        'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip\n',
    'Dockerfile': 'FROM registry.bbh.com/base/java:21\n',
    'src/main/java/com/bbh/cert/Scanner.java': 'class Scanner {}\n',
    'README.md': '# certscanner-api\n',
}

EXISTING_JOB = """<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job">
  <description>Builds certscanner-api on every push</description>
  <properties>
    <org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty/>
    <jenkins.model.BuildDiscarderProperty>
      <strategy class="hudson.tasks.LogRotator">
        <daysToKeep>30</daysToKeep><numToKeep>20</numToKeep>
        <artifactDaysToKeep>-1</artifactDaysToKeep><artifactNumToKeep>-1</artifactNumToKeep>
      </strategy>
    </jenkins.model.BuildDiscarderProperty>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.StringParameterDefinition>
          <name>AGENT_NAME</name><description>The agent to build on</description>
          <defaultValue>linux-agent-17</defaultValue><trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.BooleanParameterDefinition>
          <name>RUN_EXTENDED_PIPELINE</name><defaultValue>false</defaultValue>
        </hudson.model.BooleanParameterDefinition>
        <hudson.model.ChoiceParameterDefinition>
          <name>TARGET_REGION</name>
          <choices class="java.util.Arrays$ArrayList"><a class="string-array">
            <string>RD</string><string>QC</string></a></choices>
        </hudson.model.ChoiceParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
    <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
      <triggers>
        <hudson.triggers.TimerTrigger><spec>H 2 * * 1-5</spec></hudson.triggers.TimerTrigger>
      </triggers>
    </org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig>
        <url>http://localhost:PORT/bitbucket/scm/cert/certscanner-api.git</url>
        <credentialsId>bitbucket-cert-ro</credentialsId>
      </hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches>
    </scm>
    <scriptPath>Jenkinsfile</scriptPath>
  </definition>
</flow-definition>
"""

BUILD = {'number': 42, 'result': 'SUCCESS', 'actions': [
    {'parameters': [{'name': 'AGENT_NAME', 'value': 'linux-agent-17'},
                    {'name': 'RUN_EXTENDED_PIPELINE', 'value': False},
                    {'name': 'TARGET_REGION', 'value': 'RD'}]},
    {'remoteUrls': ['http://localhost/bitbucket/scm/cert/certscanner-api.git'],
     'lastBuiltRevision': {'branch': [{'name': 'refs/remotes/origin/main'}]}}]}

created = {}


class Stub(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def reply(self, status, body='', kind='application/json', headers=()):
        data = body if isinstance(body, bytes) else body.encode()
        self.send_response(status)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(data)))
        for name, value in headers:
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(data)

    def signed_in(self):
        expected = 'Basic ' + base64.b64encode(f'{USER}:{SECRET}'.encode()).decode()
        return self.headers.get('Authorization') == expected

    def do_GET(self):
        url = urlparse(self.path)
        path = unquote(url.path)
        if isinstance(self.connection, ssl.SSLSocket):
            return self.ois(path)
        if not self.signed_in():
            return self.reply(401, '{"error":"sign in as the generic user"}')
        repository = '/bitbucket/rest/api/1.0/projects/CERT/repos/certscanner-api'
        if path == repository + '/files':
            return self.reply(200, json.dumps({'values': sorted(SOURCES), 'isLastPage': True}))
        if path.startswith(repository + '/raw/'):
            file = path[len(repository) + 5:]
            return self.reply(200, SOURCES[file], 'text/plain') if file in SOURCES else self.reply(404)
        if path == '/jenkins/whoAmI/api/json':
            return self.reply(200, json.dumps({'name': USER, 'authenticated': True, 'anonymous': False}))
        if path == '/jenkins/crumbIssuer/api/json':
            return self.reply(200, json.dumps({'crumbRequestField': 'Jenkins-Crumb', 'crumb': 'stub-crumb'}),
                              headers=[('Set-Cookie', 'JSESSIONID.stub=session; Path=/jenkins; HttpOnly')])
        job = '/jenkins/job/CERT/job/certscanner-api/'
        if path == job + 'config.xml':
            return self.reply(200, EXISTING_JOB.replace('PORT', str(self.server.server_port)), 'application/xml')
        if path in (job + 'lastBuild/api/json', job + '42/api/json'):
            return self.reply(200, json.dumps(BUILD))
        if path.startswith('/jenkins/') and path.endswith('/api/json'):
            known = path[:-len('api/json')] in created or path == job + 'api/json'
            return self.reply(200 if known else 404, '{"name":"job"}' if known else '')
        if path.startswith('/jenkins/') and path.endswith('/config.xml') and path[:-len('config.xml')] in created:
            return self.reply(200, created[path[:-len('config.xml')]], 'application/xml')
        return self.reply(404)

    def do_POST(self):
        url = urlparse(self.path)
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        if not self.signed_in():
            return self.reply(401)
        if url.path.endswith('/createItem') and url.path.startswith('/jenkins/'):
            if self.headers.get('Jenkins-Crumb') != 'stub-crumb' or 'JSESSIONID.stub' not in self.headers.get(
                    'Cookie', ''):
                return self.reply(403, headers=[('X-Error', 'No valid crumb was included in the request')])
            name = parse_qs(url.query).get('name', [''])[0]
            address = unquote(url.path[:-len('createItem')]) + 'job/' + name + '/'
            if address in created:
                return self.reply(400, headers=[('X-Error', f'A job already exists with the name {name}')])
            created[address] = body.decode()
            print(f'Jenkins stand-in: created {address}\n{created[address]}', flush=True)
            return self.reply(200)
        return self.reply(404)

    def ois(self, path):
        if path != '/service/a2a/v4/Credentials':
            return self.reply(404)
        if self.headers.get('Authorization') != 'A2A ' + API_KEY:
            return self.reply(403, '{"Message":"unknown API key"}')
        return self.reply(200, json.dumps(SECRET))

    def log_message(self, pattern, *values):
        print('%s %s' % (self.address_string(), pattern % values), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--port', type=int, default=8099)
    parser.add_argument('--ois-cert')
    parser.add_argument('--ois-key')
    parser.add_argument('--ois-client-ca')
    options = parser.parse_args()
    if options.ois_cert:
        ois = ThreadingHTTPServer(('127.0.0.1', options.port + 1), Stub)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(options.ois_cert, options.ois_key)
        context.load_verify_locations(options.ois_client_ca)
        context.verify_mode = ssl.CERT_REQUIRED
        ois.socket = context.wrap_socket(ois.socket, server_side=True)
        threading.Thread(target=ois.serve_forever, daemon=True).start()
        print(f'OIS stand-in on https://localhost:{options.port + 1} (client certificate required)', flush=True)
    print(f'Bitbucket and Jenkins stand-ins on http://localhost:{options.port}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', options.port), Stub).serve_forever()


if __name__ == '__main__':
    main()
