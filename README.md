<h1 align="center">KCP implemented by kotlin</h1>

[![Github](https://img.shields.io/badge/GitHub-white.svg?style=flat-square&logo=github&logoColor=181717)](https://github.com/tagwan/kotlin-kcp)
![GitHub](https://img.shields.io/github/license/tagwan/kotlin-kcp)
![GitHub stars](https://img.shields.io/github/stars/tagwan/kotlin-kcp.svg)
![GitHub forks](https://img.shields.io/github/forks/tagwan/kotlin-kcp.svg)
![GitHub issues](https://img.shields.io/github/issues-raw/tagwan/kotlin-kcp?label=issues)
![GitHub last commit](https://img.shields.io/github/last-commit/tagwan/kotlin-kcp.svg)

<div align="center">

This implementation uses [Netty](https://github.com/netty/netty),
offering the full feature set of the transport protocol, while providing
room for extension with any plugins or custom behavior.
</div>


## Features
* Recylable objects:
    * Heavily used objects are recycled.
    * Reduces GC pressure.
    * Instrumented with Netty leak detection.
* Strict Netty patterns:
    * Uses Bootstrap and ServerBootstrap pattern.
    * Signals backpressure using Channel writability.
    * Uses Netty ChannelOptions for channel config.
    * Follows the normal *bind* and *connect* patterns.
    * Accurate promise responses for *write*, *connect* and others.
* 0-copy buffer interactions:
    * Retained buffer references throughout.
    * Composite buffers used for encapsulation and defragmentation.
* Easy-to-use data streaming interface:
    * Configurable packet ID used for raw ByteBuf writing and reading.
    * Extensible to allow for multiple packet ID and channel configurations.
    * True to Netty form, the pipeline can be modified and augmented as needed.
* Advanced flow control
    * Back pressure signals useful for buffer limiting when client is overloaded.
    * Pending frame-set limits reduce unnecessary resends during high transfer rates.
    * Resend priority based on frame sequence so you get older packets faster.
* Automated flush driver
    * Recommended to write to pipeline with no flush.
    * Flush cycles condense outbound data for best use of MTU.
  