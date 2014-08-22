import groovy.json.*
import groovy.xml.*
import org.xml.sax.SAXParseException
import javax.xml.transform.TransformerException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskExecutionException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import com.marklogic.client.FailedRequestException
import com.marklogic.client.DatabaseClientFactory
import com.marklogic.client.DatabaseClientFactory.Authentication
import com.marklogic.client.io.StringHandle
import org.jdom2.transform.JDOMSource
import org.jdom2.input.SAXBuilder
import org.jdom2.IllegalDataException

import org.gradle.api.tasks.TaskAction

public class StackTransform extends DefaultTask {

    def start = "/home/cgreer/stackoverflow/"
    def parseType = "comments"
    def parseFile = "xaa"
    protected props = new Properties()
    protected client
    protected docMgr

    StackTransform() {
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
    }

    def slurper = new XmlParser()

    def factory = TransformerFactory.newInstance()
    def transformer = factory.newTransformer(new StreamSource(new File("markdown.xsl")))
    def builder = new SAXBuilder("org.ccil.cowan.tagsoup.Parser")
    

    String toMarkdown(body) {
        builder.setFeature("http://xml.org/sax/features/namespaces", false)
        StringWriter writer = new StringWriter()
        def wf = body 
        try {
            def doc = builder.build(new StringReader(wf))
            transformer.transform(new JDOMSource(doc), new StreamResult(writer))
        } catch (IllegalDataException e) {
            logger.error("Skipping a post because of illegal XML character")
        }
        logger.info writer.toString()
        return writer.toString()
    }

    void parsePost(rowFile, writeSet) {
        logger.info("LINE: " + rowFile)
        def row = slurper.parseText(rowFile)
        def postType = "/question/"
        if (row.attribute("PostTypeId") == "2") { postType = "/answer/" }
        def json = new JsonBuilder()
        def root = json {
            id row.attribute("Id")
            parentId row.attribute("ParentId")
            creationDate row.attribute("CreationDate")
            body toMarkdown(row.attribute("Body"))
            ownerUserId row.attribute("OwnerUserId")
            lastEditorUserId row.attribute("LastEditorUserId")
            lastEditDate row.attribute("LastEditDate")
            lastActivityDate row.attribute("LastActivityDate")
            acceptedAnswerId row.attribute("AcceptedAnswerId")
            title row.attribute("Title")
            tags row.attribute("Tags")
        }
        writeSet.add(postType + row.attribute("Id") + ".json",
               new StringHandle(json.toString()))
    }

    void parseVote(rowFile, writeSet) {
        def row = slurper.parseText(rowFile)
        // println "Parsing " + row
        //println "Attrs: " + row.attribute("Id")
        def json = new JsonBuilder()
        def root = json {
                id row.attribute("Id")
                postId row.attribute("PostId")
                creationDate row.attribute("CreationDate")
                voteTypeId row.attribute("VoteTypeId")
        }
        writeSet.add("/vote/" + row.attribute("Id") + ".json",
               new StringHandle(json.toString()))
    }

    void parseComment(rowFile, writeSet) {
        def row = slurper.parseText(rowFile)
        def json = new JsonBuilder()
        def root = json {
                id row.attribute("Id")
                postId row.attribute("PostId")
                text row.attribute("Text")
                userId row.attribute("UserId")
                creationDate row.attribute("CreationDate")
        }
        writeSet.add("/comment/" + row.attribute("Id") + ".json",
               new StringHandle(json.toString()))
    }

    void parseUser(rowline, writeSet) {
        def row = slurper.parseText(rowline)
            logger.info("parsing a user")
            def json = new JsonBuilder()
            def root = json {
                id row.attribute("Id")
                    reputation row.attribute("Reputation")
                    displayName row.attribute("DisplayName")
                    aboutMe toMarkdown(row.attribute("AboutMe"))
                    websiteUrl row.attribute("WebsiteURL")
                    location row.attribute("Location")
            }
        writeSet.add("/contributors/" + row.attribute("Id") + ".json",
               new StringHandle(json.toString()))
    }



    @TaskAction
    void load() {
        def sourceFile = new File(start + parseFile)
        def BATCH_SIZE = 1000
        def numWritten = 0
        def writeSet = docMgr.newWriteSet()
        sourceFile.eachLine() {
            numWritten++;
            if ( numWritten % BATCH_SIZE == 0) {
                logger.info("Writing batch")
                docMgr.write(writeSet)
                writeSet = docMgr.newWriteSet()
            }
            try {
                switch ( parseType ) {
                    case "users":
                        parseUser(it, writeSet)
                        break
                    case "votes":
                        parseVote(it, writeSet)
                        break
                    case "comments":
                        parseComment(it, writeSet)
                        break
                    case "posts":
                        parsePost(it, writeSet)
                        break
                    default:
                        println "Unimplemented parseType"
                }
             } 
             catch (TransformerException e) {
                logger.error(e.getMessage())
            }
             catch (SAXParseException e) {
                logger.error(e.getMessage())
            }
        }
        try {
            if (numWritten % BATCH_SIZE > 0) {
                docMgr.write(writeSet)
            }
        } catch (FailedRequestException e) {
            logger.error("Failed Request")
            e.printStackTrace()
        }
    }
}
