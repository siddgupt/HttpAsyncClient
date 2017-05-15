# HttpAsyncClient
A simple Client for Http POST and GET request that is based on Java 1.7 Asynchronous NIO Channels.
The client allows submitting multiple requests to a Server and returns Futures for each request. The future can be used by the caller to get the parsed Http Response.

This client uses Jackson 2 to parse the Json Entity in the response from a Rest API

A Test is provided to connect to a localhost server running at 8080 using the client
