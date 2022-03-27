# paxos

The plan is to produce a minimal example of Paxos Consensus algorithm implementation.

For simplicity - we will attempt to create a replicated log where values stored are fixed length (64 bits). 
This is mainly to simplify the Transaction log implementation.

### Planned Modules

* net - NIO based network protocol
* log - Transaction Log 
* basic - Basic Paxos  
* multi - Multi Paxos

### Examples

* echoclient - simple example of echo client using net module
* echoserver - simple example of echo server using net module