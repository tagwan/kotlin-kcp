package io.netty.kcp.core

const val IKCP_RTO_NDL = 30 // no delay min rto

const val IKCP_RTO_MIN = 100 // normal min rto

const val IKCP_RTO_DEF = 200
const val IKCP_RTO_MAX = 60000
const val IKCP_CMD_PUSH = 81 // cmd: push data

const val IKCP_CMD_ACK = 82 // cmd: ack

const val IKCP_CMD_WASK = 83 // cmd: window probe (ask)

const val IKCP_CMD_WINS = 84 // cmd: window size (tell)

const val IKCP_ASK_SEND = 1 // need to send IKCP_CMD_WASK

const val IKCP_ASK_TELL = 2 // need to send IKCP_CMD_WINS

const val IKCP_WND_SND = 32
const val IKCP_WND_RCV = 32
const val IKCP_MTU_DEF = 1400
const val IKCP_ACK_FAST = 3
const val IKCP_INTERVAL = 100
const val IKCP_OVERHEAD = 24
const val IKCP_DEADLINK = 10
const val IKCP_THRESH_INIT = 2
const val IKCP_THRESH_MIN = 2
const val IKCP_PROBE_INIT = 7000 // 7 secs to probe window size

const val IKCP_PROBE_LIMIT = 120000 // up to 120 secs to probe window

const val IKCP_SN_OFFSET = 12