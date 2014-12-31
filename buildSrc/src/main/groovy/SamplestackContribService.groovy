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

public class SamplestackContribService extends SamplestackService {

    SamplestackContribService() {
       super()
    }

    @TaskAction
    void getUsers() {
        super.threadedThings("contributors", "make-contribs")
    }
}
