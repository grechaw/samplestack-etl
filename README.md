samplestack-etl
===============

ETL scripts for moving Stack Overflow Data around.


This is a standalone etl application that can parse the XML data dump from

https://archive.org/details/stackexchange

Into JSON and load it to MarkLogic.
It also installs a transform that can denormalize the documents into the data for Samplestack.

To traverse the corpus after you've loaded some posts/answers/questions/votes/users

 ```curl "http://localhost:8006/v1/search?start=1&format=json&directory=/question/" 
         | python -mjson.tool 
         | grep href
 ```

increment start...

For each URL, 
curl "http://localhost:8006/v1/documents?uri=%2Fquestion%2F19241085.json&transform=make-questions"

to get the denormalized join of all the documents.

