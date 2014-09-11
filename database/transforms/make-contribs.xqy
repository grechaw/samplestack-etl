xquery version "1.0-ml";

module namespace make-contribs = "http://marklogic.com/rest-api/transform/make-contribs";

declare default function namespace "http://www.w3.org/2005/xpath-functions";
declare namespace html = "http://www.w3.org/1999/xhtml";
declare namespace search = "http://marklogic.com/appservices/search";


declare option xdmp:mapping "false";


declare function make-contribs:votes($user-id) {
    (: need a function to get votes for a user :)
    map:entry("votes", json:array(  ) )
};


declare function make-contribs:transform(
    $context as map:map,
    $params as map:map,
    $content as document-node()
) as document-node()
{
    let $q := $content/object-node()
    let $votes := make-contribs:votes($q//id)
    let $data :=
        map:new(
            (
                map:entry("com.marklogic.samplestack.domain.Contributor",
                map:new( 
                    map:entry("id", "c" || $q//id)
                    + map:entry("reputation", $q//reputation)
                    + map:entry("displayName", $q//displayName)
                    + map:entry("userName", $q//userName)
                    + map:entry("aboutMe", $q//aboutMe)
                    + map:entry("websiteUrl", $q//websiteUrl)
                    + map:entry("location", $q//location)
                    + $votes))))
       return
           document {
               xdmp:to-json($data)
           }
};
