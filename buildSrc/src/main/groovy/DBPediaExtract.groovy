import groovy.json.*
import groovyx.net.http.RESTClient
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import groovyx.net.http.AsyncHTTPBuilder
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.PUT
import static groovyx.net.http.Method.POST

public class DBPediaExtract extends DefaultTask {

    def host = project.host
    def name = "samplestack"
    def labelsFile = new File("/media/cgreer/6488EF9588EF63D0/dbpedia/labels_en.ttl")
    def subjects = []

    def outputFile = new File("categories.nt")
    
        

    def http = new AsyncHTTPBuilder(
            poolSize : 120,
            uri : "http://cgreer.marklogic.com:8006/v1/graphs/sparql",
            contentType : "application/sparql-query" )


    @TaskAction
    void doAll() {
        findResources()
    }

    void findResources() {
        def tagsFile = new File("stackoverflowtags.txt")
        def tags = tagsFile.readLines()
        labelsFile.eachLine() {
            def m = it =~ /.*"(.*)"@en/
            def resourceUri
            def endPat = ~/>.*$/
            m.each { match ->
                if (tags.contains(match[1].toLowerCase())) {
                    def labelTriple = match[0]
                    def subject = labelTriple.replaceAll(endPat,">")
                    subjects.push(subject)
                    // def query = """
// CONSTRUCT { ${subject} ?p ?o} where { ${subject} ?p ?o}
// """
                    // runQuery(query)
                    def query = """
prefix skos: <http://www.w3.org/2004/02/skos/core#>
prefix dc: <http://purl.org/dc/terms/>
CONSTRUCT { ?o skos:broader ?b} where { ${subject} dc:subject ?o.
?o skos:broader ?b}
"""
                    runQuery(query)
                }
            }
            
        } 
    }
    
    void runQuery(query) {
        logger.warn("Running sparql " + query)
        http.getEncoder().putAt("application/n-triples", http.getEncoder().getAt("text/plain"))
        http.getEncoder().putAt("application/sparql-query", http.getEncoder().getAt("text/plain"))
        http.auth.basic "admin", "admin"
        http.handler.failure = { resp ->
           println "Unexpected Failure: ${resp.statusLine}"
        }

        def response =  http.request(POST) { req ->
            headers.ContentType = "application/sparql-query"
            headers.Accept = "application/n-triples"
            body = query 
            response.success = { resp, data ->
                outputFile << data
            }
        }

        while (! response.done ) {
            logger.warn("waiting...")
            Thread.sleep(200)
        }
    }

}
