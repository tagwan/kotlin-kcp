KCP implemented by Netty and Akka
===
<div align="center">

KCP是一个快速可靠协议，能以比 TCP 浪费 10%-20% 的带宽的代价，换取平均延迟降低 30%-40%，且最大延迟降低三倍的传输效果。纯算法实现，并不负责底层协议（如UDP）的收发，需要使用者自己定义下层数据包的发送方式，以 callback的方式提供给 KCP。 连时钟都需要外部传递进来，内部不会有任何一次系统调用。


</div>

Stats
===
![Alt](https://repobeats.axiom.co/api/embed/5de133ecad8e3069a82bfa042b6fab6104ea141e.svg "Repobeats analytics image")


