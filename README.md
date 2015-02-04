samplestack-etl
===============

Updated for StackOverflow Achive from 09/2014

ETL scripts for moving Stack Overflow Data around.

This is a standalone etl application that starts with the data available 
from

https://archive.org/details/stackexchange

And includes tools for loading this data into Marklogic, and transforming
it into document-oriented JSON documents for use in [Samplestack](http://github.com/marklogic/marklogic-samplestack).

The process is largely automated, but as the data dumps are large and the
processes take a long time, I found it necessary to include options
for partial runs and restarting in the middle.  Thus this is more a toolbox
than a long-running or 100% fool-proof ETL.  In fact, I'm finding
several places that could be improved as I'm running through this README...

The ETL process:

1. Use your favorite torrent client to download Stack Overflow archives.
   We will not use all of the files, so if you want to keep the download a 
   little smaller skip PostHistory and PostLinks.

2. Use 7z to uncompress each file.  You'll now have several XML files in 
   some directory of your choosing.  We'll call it $STACK_DATA, but for now 
   I notice there's a default in `buildSrc/src/main/groovy/StackTransform.groovy`
   but if you're used to gradle at all you'll figure out how to override.

3. Have TWO MarkLogic instances ready, with at least 300G disk space available to
   each one.  I ran the ETL on two separate virtual single-node clusters, driving
   the client from a third machine.  There are many ways one could improve the
   throughput, but this process ended up just tolerable.

4.  This project depends on an XHTML to Markdown stylesheet that is not
    distributed here for licensing reasons -- so you have to go get this fine
    product and put it in the root of your ETL project for it to work.
    http://www.lowerelement.com/Geekery/XML/markdown.xsl

5. Run `./gradlew dbconfigure`  If configured correctly, this command will
   install two transforms on one of the above servers, which will be used in
   the second step of the Samplestack ETL.

6.  The following commands, if they all complete, will load all the data into 
   the first MarkLogic instance (watch for hard coded things until further notice).
   This whole process was in the 15-20 hour range, with a little naive parallelism.

```
./gradlew loadUsers -PparseFile=Users.xml`
./gradlew loadPosts -PparseFile=Posts.xml`
./gradlew loadComments -PparseFile=Comments.xml`
./gradlew loadTags -PparseFile=Tags.xml`
./gradlew loadVotes -PparseFile=Votes.xml`
```
 
==take a breath==

7.  At this point, you have a MarkLogic database with one document that
    corresponds to each of the LINES in the XML files above.  In effect, every
    row from Stack Overflow is now in its own document.  Relational databases,
    built around joins, tend toward data with this kind of structure.  The second
    step will chase down all the references among these row-based documents and
    assemble the JSON documents ones we'll actually use in Samplestack

```
./gradlew makeSamplestackDocs -Ppage=1
./gradlew makeSamplestackUsers -Ppage=1
```

Actually now that I look at that-- I wrote a shell script to iterate through
all the 'pages'.  page constructs, extracts, and loads about 10000 users or qna
Documents...  The process takes a few days on our project setup.  Each one uses
a stock REST API server with a document read transform implemented in XQuery to
join the documents together.  It wraps that in some bulk read/write requests,
as well as a little threading to keep MarkLogic busy, and off it goes!

8. The last step involved extracting a sample of data from this corpus to make
   the samplestack seed-data1.8.1.  This involved some direct work with the
   data on the destination machine, and is even less of a science than the above
   ETL work.  That said, simply use the enclosed qconsole workspace, called samplestack-export.xml.  Import that into qconsole on your destination database, and you'll see four queries.  The first is just to see how many documents are loaded.

Query 2, top-users -- finds all the most active users who have authored questions along the lines of certain criteria.  It inserts the results of this query into a document for furthre use.

Query 2, exports.  Reads the users in the file from the above step and exports all documents two whose questions, comments, or answers those top users contributed.

On the filesystem, I used grep to find all the users who contributed to those docunments.

Query 3 looks at the file I made on the filesystem, and gets all the contributor records for the exported questions.

TODO RDF data.
