<?php
//this is a terible patch to get around not being able to do the OPTIONS method
//when requesting from the gpodder api. the client can do a post request to
//this containing the base 64 login string for the basic auth header.
//because there's no Authorization header its considered a simple Request by CORS,
//and will be able to do an POST request instead of a CORS preflight OPTIONS
//to make sure its allowed to use certain methods with an endpoint.

//esentialy this is an api proxy

//helper to make sure we have post data
function request_has($feild){
    return isset($_POST[$feild]);
}

//header to allow CORS
header("Access-Control-Allow-Origin: *");

if(!(request_has("auth")
  && request_has("endpoint")
  && request_has("method"))){
    //make sure we have the right form data
    http_response_code(400);
    echo "request error: auth, method, and endpoint";
}else if($_SERVER["REQUEST_METHOD"] !== "POST"){
    //only post is allowed
    http_response_code(405);
    echo "method not alowed";    
}else{
    //setup vars for request
    $auth = $_POST["auth"];
    $endpoint = $_POST["endpoint"];
    $method = $_POST["method"];
    $curl_session = curl_init();
    $url = "https://www.gpodder.net$endpoint";
    $header = array("Authorization: Basic $auth");
    
    //initial curl setup
    curl_setopt($curl_session, CURLOPT_URL, $url);
    curl_setopt($curl_session, CURLOPT_HEADER, false);
    curl_setopt($curl_session, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($curl_session, CURLOPT_HTTPHEADER, $header);

    //method specific stuff
    switch($method){
    case "POST":
        curl_setopt($curl_session, CURLOPT_POST, true);
        if(isset($_POST["data"])){
            //send the user's post data
            curl_setopt($curl_session, CURLOPT_POSTFIELDS, $_POST["data"]);
        }else{
            //the post data can be anything.
            //this default is here to make php curl happy
            curl_setopt($curl_session, CURLOPT_POSTFIELDS, "foo=bar");
        }
        break;
    case "GET":
        //curl defaults to get so we dont have to do anything
        break;
    default:
        http_response_code(501);
        echo "method not yet implemented";
        exit(1);
    }
    
    //vars to capture results
    $result = curl_exec($curl_session);
    $api_response_code = curl_getinfo($curl_session, CURLINFO_HTTP_CODE);
    
    //respond with the same response code and response as the api
    http_response_code($api_response_code);
    curl_close($curl_session);
    echo $result;
}
?>