# jsonrpcservlet

A Java Servlet for handling JSON-RPC 2.0 requests.

## Overview

`jsonrpcservlet` is a lightweight and easy-to-use Java Servlet designed to 
simplify the implementation of JSON-RPC 2.0 services within your web applications.
It leverages `io.github.ralfspoeth:json` for JSON processing and integrates 
seamlessly with standard Servlet containers.

## Features

*   Handles JSON-RPC 2.0 requests.
*   Integrates with `jakarta.servlet-api`.
*   Uses `io.github.ralfspoeth:json` for efficient JSON parsing and generation.
*   Supports method invocation and error handling as per JSON-RPC 2.0 specification.

## Installation

To include `jsonrpcservlet` in your Maven project, add the following dependency 
to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.ralfspoeth</groupId>
    <artifactId>jsonrpcservlet</artifactId>
    <version>1.0.0-SNAPSHOT</version> <!-- Or the latest release version -->
</dependency>
```

## Usage

(Further details on how to configure and use the servlet will be added here, 
including examples of how to define RPC methods and register them with the servlet.)

## Building from Source

This project uses Maven. To build it, navigate to the project root and run:

```bash
mvn clean install
```
## License

MIT. Copyright 2026 Ralf Spöth.
