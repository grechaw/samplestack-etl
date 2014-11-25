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
import com.marklogic.client.io.DocumentMetadataHandle
import com.marklogic.client.io.DocumentMetadataHandle.Capability
import org.jdom2.transform.JDOMSource
import org.jdom2.input.SAXBuilder
import org.jdom2.IllegalDataException
import java.net.URLEncoder

import org.gradle.api.tasks.TaskAction

public class SamplestackService extends DefaultTask {

    protected props = new Properties()
    protected client
    protected docMgr
    protected targetClient
    protected targetDocMgr
    def THREADWIDTH = 20
    def PAGE_SIZE = 500  // for easy math make TH*P = 10000
    def page = 1
    def search = ""

    SamplestackService() {
       super()
       project.file("gradle.properties").withInputStream { props.load(it) }
       ext.config = new ConfigSlurper().parse(props)
       client = DatabaseClientFactory.newClient(
                   config.marklogic.rest.host,
                   Integer.parseInt(config.marklogic.rest.port),
                   config.marklogic.admin.user,
                   config.marklogic.admin.password,
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

    void threadedThings(directory, transformName) {
        def th = []
        /* ten wide loop */
        for ( i in 0.. (THREADWIDTH - 1) ) {
            th.push(Thread.start {
                    getThings(directory, transformName, i)
                })
            sleep(1000)
        }
        for ( t in th ) { 
            t.join()
        }
    }

    void getThings(directory, transformName, part) {
        def params = [:]
        def start = 1 + ((Integer.parseInt(page) - 1) * PAGE_SIZE * THREADWIDTH)
        def limit = start + PAGE_SIZE
        start += PAGE_SIZE * part
        limit += PAGE_SIZE * part
        logger.info("Starting page " + page + ", part " + part + ", page size " + PAGE_SIZE)
        def url = "http://" + config.marklogic.rest.host + ":" + config.marklogic.rest.port + "/v1/values/uris?directory=/" + directory + "/&format=json&options=doclist&start=" + start + "&limit=" + limit + "&q=" + java.net.URLEncoder.encode(search)
        logger.debug url
        RESTClient client = new RESTClient(url)
        client.auth.basic config.marklogic.writer.user, config.marklogic.writer.password
        def response = client.get(params)
        def json = response.data
        def results = json["values-response"]["distinct-value"]
        if (results == null) {
            logger.info("No results on page " + page + " part " + part)
            return
        }
        def st = new ServerTransform(transformName)
        def writeSet = targetDocMgr.newWriteSet()
        def acceptedPermissionMetadata = new DocumentMetadataHandle().withPermission("samplestack-guest", Capability.READ)
        def pojoCollectionMetadata = new DocumentMetadataHandle().withCollections("com.marklogic.samplestack.domain.Contributor").withPermission("samplestack-guest", Capability.READ)
        def readUris = new java.util.ArrayList()
        def hrefs = results.each {  result ->
                        def docUri = result._value
                        readUris.add(docUri)
        }
        logger.debug("Getting " + readUris.size() + " docs.")
        def readSet = docMgr.read(st, null, readUris.toArray(new String[1]))
        while (readSet.hasNext()) {
                def docRecord = readSet.next()
                def docUri = docRecord.getUri()
                def docHandle = docRecord.getContent(new StringHandle())
                def newUri = docUri.replaceAll(~"question/", "questions/soq")
                newUri = newUri.replaceAll(~"/contributors/", "com.marklogic.samplestack.domain.Contributor/sou")
                logger.debug("processing page run... " + docUri + " to " + newUri)
                def fileUri = "database/seed-data/" + newUri
                def outputFile = new File(fileUri)
                outputFile.delete()
                outputFile = new File(fileUri)
                def jsonString = docHandle.get()
                logger.debug("Creating file " + newUri)
                outputFile << jsonString
                def handle2 = new StringHandle()
                handle2.set(jsonString)

            // this part loads marklogic too
                if (docHandle.get().contains("acceptedAnswerId")) {
                    writeSet.add(newUri, acceptedPermissionMetadata, handle2)
                } else if (docHandle.get().contains("domain.Contributor")) {
                    writeSet.add(newUri, pojoCollectionMetadata, handle2)
                } else {
                    writeSet.add(newUri, handle2)
                        }
                }
        targetDocMgr.write(writeSet)
        
        logger.info("Wrote page number "+ page + " part " + part)
    }
}
