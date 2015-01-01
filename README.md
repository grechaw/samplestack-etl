samplestack-etl
===============

Updated for StackOverflow Achive from 09/2014

ETL scripts for moving Stack Overflow Data around.

This is a standalone etl application that can parse the XML data dump from

https://archive.org/details/stackexchange

and using two MarkLogic databases prepares the data for Samplestack.

The process consists of 

* initial load steps, to fill a MarkLogic database with
all of the comments, users, votes, and posts from the archive.

These commands, if they all complete, will load all the data 
(watch for hard coded things until further notice)

```
./gradlew loadUsers -PparseFile=Users.xml`
./gradlew loadPosts -PparseFile=Posts.xml`
./gradlew loadComments -PparseFile=Comments.xml`
./gradlew loadTags -PparseFile=Tags.xml`
./gradlew loadVotes -PparseFile=Votes.xml`
```
 
* Two ETL steps, to load a second MarkLogic database with documents assembled from the first one.

```
./gradlew makeSamplestackDocs -Ppage=1
./gradlew makeSamplestackUsers -Ppage=1
```

Actually these are wrapped in shell scripts to do a page at a time... but more on that later.


This project depends on an XHTML to Markdown stylesheet that is not distributed here for licensing reasons.

It's available here:
http://www.lowerelement.com/Geekery/XML/markdown.xsl

Put it in the root directory of this project


=Notes about further steps

To extract the 1.7 sample,

once I have a dump of questions, pipe each through python -mjson.tool, then

for $x in *.json; do python -mjson.tool $x > $x.j ; done
grep '"id": "sou' *.j

then vim it and through sort -u
