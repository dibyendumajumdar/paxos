# paxos

The goal of this project is to implement a minimal example of Paxos Consensus algorithm.

For simplicity - we will attempt to create a replicated log where values stored are fixed length (64 bits long values only). 

## Modules

* net - NIO based network protocol
* log - Transaction Log (ledger)
* basic - Basic Paxos  
* multi - Multi Paxos

## Reading Material

The implementation of Paxos is based on the description provided by Leslie Lamport in the paper
[The Part-Time Parliament](http://lamport.azurewebsites.net/pubs/lamport-paxos.pdf). Some terminology
used stems from this paper, e.g. Ledger, Decree, Ballot, etc. The message definitions follow this paper
rather than the subsequent papers where Leslie used other terminology such as Phase 1a, etc.
Where possible these alternative terms are referred to in logging and in comments.

Some terms from the original paper have been amended to match the terminology used in [Leslie's TLA+ specification
of Paxos](http://lamport.azurewebsites.net/tla/st-pete-lecture-exercises.zip). Also see video
lectures entitled [The Paxos Algorithm or How to Win a Turing Award](https://lamport.azurewebsites.net/tla/paxos-algorithm.html).

## Description of Paxos Algorithms

I attempt to describe the algorithms implemented in a programmer friendly way rather than using TLA+.

* [Basic Paxos](basic/Basic-Paxos.md)
* [Multi Paxos](multi/Multi-Paxos.md)


## LICENSE

MIT License