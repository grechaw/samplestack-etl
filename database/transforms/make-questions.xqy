xquery version "1.0-ml";

module namespace make-questions = "http://marklogic.com/rest-api/transform/make-questions";

declare default function namespace "http://www.w3.org/2005/xpath-functions";
declare namespace html = "http://www.w3.org/1999/xhtml";
declare namespace search = "http://marklogic.com/appservices/search";


declare option xdmp:mapping "false";

declare function make-questions:user($user-id) {
    cts:search(collection(), cts:and-query( (cts:directory-query("/contributors/"), cts:json-property-range-query("id","=", $user-id))), "unfiltered") ! 
      map:new(
          ( map:entry("id", concat("sou", string(.//id))), 
            map:entry("originalId", .//id),
            map:entry("userName", replace(.//userName/string(), "email.com", "example.com")),
            map:entry("displayName",  .//displayName)) )
};



declare function make-questions:comments($post-id) {
    let $comments :=
        cts:search(collection(), cts:and-query( (cts:directory-query("/comment/"), cts:json-property-range-query("postId","=", $post-id))), "unfiltered" ) ! ./node()
    return array-node {
        for $c in $comments
        return 
            map:entry("id", concat("soc", string($c/id))) +
            map:entry("text", $c/text) +
    map:entry("creationDate", if ($c/creationDate) then $c/creationDate/string() || "Z" else ()) +
            map:entry("owner", make-questions:user($c/userId))
    }
};

declare function make-questions:votes($map, $post-id) {
    let $votes := cts:search(collection(), cts:and-query( (cts:directory-query("/vote/"), cts:json-property-range-query("postId","=", $post-id))), "unfiltered") ! ./node()
    let $up-votes := count($votes[voteTypeId = "2"])
    let $down-votes := count($votes[voteTypeId = "3"])
    let $upvoters-array := json:array()
    let $_ := json:array-resize($upvoters-array, $up-votes, "unknown")
    let $downvoters-array := json:array()
    let $_ := json:array-resize($downvoters-array, $down-votes, "unknown")
    return
        (
        map:put($map, "itemTally", $up-votes - $down-votes),
        map:put($map, "upvotingContributorIds", $upvoters-array),
        map:put($map, "downvotingContributorIds", $downvoters-array)
        )
};


declare function make-questions:answers($post-id, $accepted-id) {
    let $answers := 
        cts:search(collection(), cts:and-query( (cts:directory-query("/answer/"), cts:json-property-range-query("parentId","=", $post-id))), "unfiltered" ) ! ./node()
    return 
        array-node {
            for $answer in $answers
            let $answer-id := data($answer/id)
            let $map := map:map()
            let $_ := map:put($map,"id", concat("soa", string($answer/id)))
            let $_ := map:put($map,"text", $answer/body) 
            let $_ := map:put($map,"creationDate", if ($answer/creationDate) then $answer/creationDate/string() || "Z" else ()) 
            let $_ := map:put($map,"comments", make-questions:comments($answer//id))
            let $_ := make-questions:votes($map, $answer-id) 
            let $_ := map:put($map,"owner", make-questions:user($answer/ownerUserId))
            return $map
        }

};

declare function make-questions:transform(
    $context as map:map,
    $params as map:map,
    $content as document-node()
) as document-node()
{
    let $q := $content/object-node()
    let $user-id := data($q/ownerUserId)
    let $post-id := data($q/id)
    let $accepted-answer-id := data($q/acceptedAnswerId)
    let $accepted := 
        if ($accepted-answer-id)
        then concat("soa", string($q/acceptedAnswerId))
        else null-node { }
    let $ownerUser := make-questions:user($user-id)
    let $comments := make-questions:comments($post-id)
    let $tags := data($q/tags)
    let $new-tags := 
        if (exists($tags))
        then array-node { for $t in tokenize($tags, "[<>]") where $t ne "" return $t }
        else array-node { }
    let $data :=
        let $map := map:map()
        let $_ := map:put($map,"id", concat("soq", string($q/id)))
        let $_ := map:put($map,"originalId", concat(string($q/id)))
        let $_ := map:put($map,"creationDate", if ($q/creationDate) then $q/creationDate/string() || "Z" else ())
        let $_ := map:put($map,"text", $q/body)
        let $_ := map:put($map,"lastActivityDate", if ($q/lastActivityDate) then $q/lastActivityDate/string() || "Z" else ())
        let $_ := map:put($map, "acceptedAnswerId", $accepted)
        let $_ := map:put($map,"title", $q/title)
        let $_ := map:put($map,"comments", make-questions:comments($post-id))
        let $_ := map:put($map,"answers", make-questions:answers($post-id, $accepted-answer-id))
        let $_ := make-questions:votes($map, $post-id)
        let $_ := map:put($map,"owner", make-questions:user($q/ownerUserId))
        let $_ := map:put($map,"tags", $new-tags)
        return $map

    let $data-json := xdmp:to-json($data)
    let $item-tallys := sum($data-json//itemTally/xs:int(.))
    let $answer-count := json:array-size(xdmp:from-json($data-json//array-node('answers')))
    let $data-with-score := 
        map:new ( (
                $data, 
                map:entry("voteCount", $item-tallys),
                map:entry("answerCount", $answer-count),
                if ($q/acceptedAnswerId/string()) 
                then map:entry("accepted", true())
                else map:entry("accepted", false())
                ))
       return
           document {
               xdmp:to-json($data-with-score)
           }
};
