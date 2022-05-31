![Alt](https://repobeats.axiom.co/api/embed/5de133ecad8e3069a82bfa042b6fab6104ea141e.svg "Repobeats analytics image")

<h1 align="center">KCP implemented by kotlin</h1>

[![Github](https://img.shields.io/badge/GitHub-white.svg?style=flat-square&logo=github&logoColor=181717)](https://github.com/tagwan/kotlin-kcp)
![GitHub](https://img.shields.io/github/license/tagwan/kotlin-kcp)
![GitHub stars](https://img.shields.io/github/stars/tagwan/kotlin-kcp.svg)
![GitHub forks](https://img.shields.io/github/forks/tagwan/kotlin-kcp.svg)
![GitHub issues](https://img.shields.io/github/issues-raw/tagwan/kotlin-kcp?label=issues)
![GitHub last commit](https://img.shields.io/github/last-commit/tagwan/kotlin-kcp.svg)

<div align="center">

KCP是一个快速可靠协议，能以比 TCP 浪费 10%-20% 的带宽的代价，换取平均延迟降低 30%-40%，且最大延迟降低三倍的传输效果。纯算法实现，并不负责底层协议（如UDP）的收发，需要使用者自己定义下层数据包的发送方式，以 callback的方式提供给 KCP。 连时钟都需要外部传递进来，内部不会有任何一次系统调用。


</div>
