<?php
//many of the rss feeds dont have the access-control-allow-orgin *
//header. this allows js apps to get an rss feed withought cors

//helper to make sure we have post data
function request_has($feild){
    return isset($_GET[$feild]);
}

//header to allow CORS
header("Access-Control-Allow-Origin: *");

if(!(request_has("url"))){
    //make sure we have the right form data
    http_response_code(400);
    echo "request error: must have url";
}else if($_SERVER["REQUEST_METHOD"] !== "GET"){
    //only post is allowed
    http_response_code(405);
    echo "method not alowed";    
}else{
    //setup vars for request
    $curl_session = curl_init();
    $url = urldecode($_GET["url"]);
    
    //initial curl setup
    curl_setopt($curl_session, CURLOPT_URL, $url);
    curl_setopt($curl_session, CURLOPT_HEADER, false);
    curl_setopt($curl_session, CURLOPT_FOLLOWLOCATION, true);
    curl_setopt($curl_session, CURLOPT_RETURNTRANSFER, true);
    
    //vars to capture results
    $result = curl_exec($curl_session);
    $response_code = curl_getinfo($curl_session, CURLINFO_HTTP_CODE);
    if(curl_errno($curl_session)){
        //if there was an error curling, tell the user the
        //usptream server didn't sweep enough and we missed
        http_response_code(502);
    }else{
        //respond with the same response code and response as the api
        http_response_code($response_code);
    }
    curl_close($curl_session);
    echo $result;
}
?>