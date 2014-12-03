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
            map:entry("userName", .//userName),
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
            map:entry("creationDate", $c/creationDate) +
            map:entry("owner", make-questions:user($c/userId))
    }
};

declare function make-questions:votes($post-id) {
    let $votes := cts:search(collection(), cts:and-query( (cts:directory-query("/vote/"), cts:json-property-range-query("postId","=", $post-id))), "unfiltered") ! ./node()
    let $up-votes := count($votes[voteTypeId = "2"])
    let $down-votes := count($votes[voteTypeId = "3"])
    return
        map:entry("itemTally", $up-votes - $down-votes)
};


declare function make-questions:answers($post-id, $accepted-id) {
    let $answers := 
        cts:search(collection(), cts:and-query( (cts:directory-query("/answer/"), cts:json-property-range-query("parentId","=", $post-id))), "unfiltered" ) ! ./node()
    return 
        array-node {
            for $answer in $answers
            let $answer-id := data($answer/id)
            return
                map:entry("id", concat("soa", string($answer/id))) +
                map:entry("text", $answer/body) +
                map:entry("creationDate", $answer/creationDate) +
                map:entry("comments", make-questions:comments($answer//id)) +
                make-questions:votes($answer-id) +
                map:entry("owner", make-questions:user($answer/ownerUserId)) 
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
        then map:entry("acceptedAnswerId", concat("soa", string($q/acceptedAnswerId)))
        else
            map:entry("acceptedAnswerId", null-node { })
    let $ownerUser := make-questions:user($user-id)
    let $comments := make-questions:comments($post-id)
    let $tags := data($q/tags)
    let $new-tags := 
        if (exists($tags))
        then array-node { for $t in tokenize($tags, "[<>]") where $t ne "" return $t }
        else array-node { }
    let $votes := make-questions:votes($post-id)
    let $data :=
        map:entry("id", concat("soq", string($q/id)))
        +
        map:entry("originalId", concat(string($q/id)))
        +
        map:entry("creationDate", $q/creationDate)
        +
        map:entry("text", $q/body)
        +
        map:entry("lastActivityDate", $q/lastActivityDate)
        +
        $accepted
        +
        map:entry("title", $q/title)
       +
       map:entry("comments", make-questions:comments($post-id))
       +
       map:entry("answers", make-questions:answers($post-id, $accepted-answer-id))
       +
       $votes
       +
       map:entry("owner", make-questions:user($q/ownerUserId))
       +
       map:entry("tags", $new-tags)

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
