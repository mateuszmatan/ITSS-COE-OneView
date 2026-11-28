package com.bbh.itss.coe.oneview.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.transform.Canonical

import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8

/**
 * A stand-in for a system OneView calls over HTTP, such as Jenkins, Bitbucket or OIS. A test says what it answers,
 * and reads back the requests it received. What no answer was given for is answered with 404.
 */
class FakeHttpServer implements AutoCloseable {

    final List<Request> requests = new CopyOnWriteArrayList<>()

    private final List<Route> routes = new CopyOnWriteArrayList<>()
    private final HttpServer server

    private FakeHttpServer() {
        server = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)
        server.createContext('/') { HttpExchange exchange -> handle(exchange) }
        server.start()
    }

    static FakeHttpServer start() {
        new FakeHttpServer()
    }

    String getUrl() {
        "http://127.0.0.1:${server.address.port}"
    }

    /** The host and port, as an address without a scheme names them. */
    String getHost() {
        "127.0.0.1:${server.address.port}"
    }

    /**
     * Answers the requests of the method whose path, with its query, matches the pattern. The answer is a map of
     * status, body, contentType and headers, or a closure that makes one from the request; the answer given last wins.
     */
    void on(String method, String pattern, Object answer) {
        routes.add(0, new Route(method, Pattern.compile(pattern), answer))
    }

    List<Request> received(String method, String pattern) {
        requests.findAll { it.method == method && it.target ==~ pattern }
    }

    @Override
    void close() {
        server.stop(0)
    }

    private void handle(HttpExchange exchange) {
        try {
            Request request = new Request(method: exchange.requestMethod, target: exchange.requestURI.toString(),
                    path: exchange.requestURI.path, headers: exchange.requestHeaders.collectEntries { name, values ->
                [name.toLowerCase(), values.join(', ')]
            }, body: exchange.requestBody.getText('UTF-8'))
            requests << request
            Route route = routes.find { it.method == request.method && request.target ==~ it.pattern }
            Map answer = route == null ? [status: 404]
                    : route.answer instanceof Closure ? route.answer.call(request) as Map : route.answer as Map
            byte[] bytes = (answer.body ?: '').toString().getBytes(UTF_8)
            exchange.responseHeaders.add('Content-Type', (answer.contentType ?: 'application/json') as String)
            (answer.headers as Map ?: [:]).each { name, value -> exchange.responseHeaders.add(name as String, value as String) }
            exchange.sendResponseHeaders((answer.status ?: 200) as int, bytes.length == 0 ? -1 : bytes.length)
            exchange.responseBody.withStream { it.write(bytes) }
        } finally {
            exchange.close()
        }
    }

    @Canonical
    static class Request {
        String method
        String target
        String path
        Map<String, String> headers
        String body
    }

    @Canonical
    private static class Route {
        String method
        Pattern pattern
        Object answer
    }
}
