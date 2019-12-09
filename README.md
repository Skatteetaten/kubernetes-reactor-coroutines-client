# Kubernetes Reactor Couroutines Client

This is a simple http client for talking with kubernetes. 

It is using `WebClient` from Spring Webflux as the http client and has support for Kotlin couroutines

Really beta for now.



 - hente en ressurs gitt man har en, 
 - oppdatere ressurs
 - lage resssurs
 - slette ressurs
 
 - liste ressurs med labelSelector. Hele som string, Map<String, String>
 - hente ut liste med ressurser returnere Flux. 
 
 - retrye 500 kall
 - behandle 404 som tom
 
 - hente ut weblient og suspended webclient
 - teste mdc felter og logging
 - logging, på debug eller trace
 
 - Websocket integrasjon
 - teste, nettverk
 