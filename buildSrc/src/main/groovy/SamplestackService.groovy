import groovy.json.*
import groovyx.net.http.RESTClient
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import com.marklogic.client.FailedRequestException
import com.marklogic.client.DatabaseClientFactory
import com.marklogic.client.DatabaseClientFactory.Authentication
import com.marklogic.client.io.StringHandle
import com.marklogic.client.document.ServerTransform
import org.jdom2.transform.JDOMSource
import org.jdom2.input.SAXBuilder
import org.jdom2.IllegalDataException

import org.gradle.api.tasks.TaskAction

public class SamplestackService extends DefaultTask {

    protected props = new Properties()
    protected client
    protected docMgr
    protected targetClient
    protected targetDocMgr
    def page = 1
    def search = ""

    SamplestackService() {
       super()
       project.file("gradle.properties").withInputStream { props.load(it) }
       ext.config = new ConfigSlurper().parse(props)
       client = DatabaseClientFactory.newClient(
                   config.marklogic.rest.host,
                   Integer.parseInt(config.marklogic.rest.port),
                   config.marklogic.writer.user,
                   config.marklogic.writer.password,
                   Authentication.DIGEST)
       docMgr = client.newJSONDocumentManager()

       targetClient = DatabaseClientFactory.newClient(
                   config.marklogic2.rest.host,
                   Integer.parseInt(config.marklogic2.rest.port),
                   config.marklogic.writer.user,
                   config.marklogic.writer.password,
                   Authentication.DIGEST)
       targetDocMgr = targetClient.newJSONDocumentManager()
    }

    void fetch(docuri) {
        def fileUri = "database/seed-data" + docuri.replace("question", "questions")
        def outputFile = new File(fileUri)
        outputFile.delete()
        outputFile = new File(fileUri)
        def handle = new StringHandle()
        def st = new ServerTransform("make-questions")
        docMgr.read(docuri, handle, st)
        def json = handle.get()
        logger.warn("Creating file " + fileUri)
        outputFile << json
    }


    @TaskAction
    void getDoc() {
        def PAGE_SIZE = 1000
        def params = [:]
        def start = 1 + ((Integer.parseInt(page) - 1) * PAGE_SIZE)
        def limit = start + PAGE_SIZE
        def url = "http://" + config.marklogic.rest.host + ":" + config.marklogic.rest.port + "/v1/values/uris?directory=/question/&format=json&options=doclist&start=" + start + "&limit=" + limit + "&q=" + search
        logger.info url
        RESTClient client = new RESTClient(url)
        client.auth.basic config.marklogic.writer.user, config.marklogic.writer.password
        def response = client.get(params)
        def json = response.data
        def results = json["values-response"]["distinct-value"]
        def st = new ServerTransform("make-questions")
        def writeSet = targetDocMgr.newWriteSet()
        def hrefs = results.each {  result ->
                        def docUri = result._value
                        def newUri = docUri.replaceAll(~"question", "questions")
                        def docHandle = new StringHandle()
                        docMgr.read(docUri, docHandle, st)
                        writeSet.add(newUri, docHandle)
                        }
        targetDocMgr.write(writeSet)
        logger.info("Wrote page number "+ page)
    }
}
