xquery version "1.0-ml";

module namespace make-questions = "http://marklogic.com/rest-api/transform/make-questions";

declare default function namespace "http://www.w3.org/2005/xpath-functions";
declare namespace html = "http://www.w3.org/1999/xhtml";
declare namespace search = "http://marklogic.com/appservices/search";


declare option xdmp:mapping "false";

declare function make-questions:user($user-id) {
  cts:search(collection(), cts:and-query( (cts:directory-query("/contributors/"), cts:json-property-range-query("id","=", $user-id))), "unfiltered") ! 
  map:new(( map:entry("id", ./id), map:entry("displayName",  ./displayName)) )
};



declare function make-questions:comments($post-id) {
   let $comments :=
     cts:search(collection(), cts:and-query( (cts:directory-query("/comment/"), cts:json-property-range-query("postId","=", $post-id))), "unfiltered" ) ! ./node()
   return array-node {
      for $c in $comments
      return $c + map:entry("owner", make-questions:user($c/userId))
   }
};

declare function make-questions:votes($post-id) {
  let $votes := cts:search(collection(), cts:and-query( (cts:directory-query("/vote/"), cts:json-property-range-query("postId","=", $post-id))), "unfiltered") ! ./node()
  let $up-votes := count($votes[voteTypeId = "2"])
  let $down-votes := count($votes[voteTypeId = "3"])
  let $_ := xdmp:log(("FOUND VOTE", $votes))
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
       $answer +
       map:entry("comments", make-questions:comments($answer//id)) +
       make-questions:votes($answer-id) +
       map:entry("owner", make-questions:user($answer/ownerUserId)) +
       map:entry("creationYearMonth", format-dateTime(data($answer/creationDate), "[Y0001][M01]"))
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
    let $ownerUser := make-questions:user($user-id)
    let $comments := make-questions:comments($post-id)
    let $data :=
        $q + 
       map:entry("docScore", sum($q//itemTally))   (: this won't work inside this transaction :)
       +
       map:entry("comments", make-questions:comments($post-id))
       +
       map:entry("answers", make-questions:answers($post-id, $accepted-answer-id))
       +
       map:entry("creationYearMonth", format-dateTime(data($q/creationDate), "[Y0001][M01]"))
       +
       make-questions:votes($post-id)
       +
       map:entry("owner", make-questions:user($q/ownerUserId))

   return
       document {
           xdmp:to-json($data)
       }
};
