import groovy.json.*
import groovyx.net.http.RESTClient
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class MarkLogicClearTask extends DefaultTask {

    protected props = new Properties()
    
    MarkLogicClearTask() {
       super()
       project.file("gradle.properties").withInputStream { props.load(it) }
       ext.config = new ConfigSlurper().parse(props)
    }

    @TaskAction
    void updateDatabase() {
        logger.error("Saving Database Configuration")
        RESTClient client = new RESTClient("http://" + config.marklogic.rest.host + ":8002/manage/v2/databases/" + config.marklogic.rest.name)
        client.auth.basic "admin", "admin"
        def params = [:]
        params.contentType = "application/json"
        params.body = '{"operation":"clear-database"}'
        def response = client.post(params)
        println response
    }

}
