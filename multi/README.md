This module contains an implementation of the [Multi-Paxos algorithm](multi/Multi-Paxos.md).

The implementation is based on the description provided by Leslie Lamport in the paper
[The Part-Time Parliament](http://lamport.azurewebsites.net/pubs/lamport-paxos.pdf). Some of the terminology used stems
from this paper, e.g. Ledger, Decree, Ballot, etc. The message definitions follow this paper rather than the subsequent
papers where Leslie used other terminology such as Phase 1a, etc. Where possible these alternative terms are referred to
in logging and in comments.

Some terms from the original paper have been amended to match the terminology used
in [Leslie's TLA+ specification of Paxos](http://lamport.azurewebsites.net/tla/st-pete-lecture-exercises.zip). Also see
video lectures
entitled [The Paxos Algorithm or How to Win a Turing Award](https://lamport.azurewebsites.net/tla/paxos-algorithm.html).

The multi-paxos algorithm is an extension of the basic-paxos algorithm described
in [here](https://github.com/dibyendumajumdar/paxos/blob/main/basic/Basic-Paxos.md).

The key enhancements are as follows:

* Each process maintains multiple decrees in its ledger, and the highest consecutive committed decree number is tracked
  as `commitNum`.
* During the protocol, each process passes its `commitNum` with the message - this allows the receiving process to
  inform the sender of any decrees committed greater than the sender's `commitNum`.
* When a new ballot is initiated, the conductor obtains agreement on all pending decrees, not just one of them. Pending
  decrees are those that are not yet committed but have been voted in some ballot.

In this version of multi-paxos there is no explicit leader election. Rather we assume that clients decide who to contact
- and are sticky to a preferred process for a period of time, and the process that is contacted, tries to become leader
if it is not already the leader.

Some other changes that are also carried over from the Basic paxos implementation are:

* `nextBal` renamed to `maxBal`
* `prevBal` renamed to `maxVBal`
* `prevDec` renamed to `maxVal`

There are also some differences in the description of the algo between the PTP paper and the TLA+ spec. I found
following [discussion of these differences on StackOverflow](https://stackoverflow.com/questions/29880949/contradiction-in-lamports-paxos-made-simple-paper)
helpful in understanding the differences.

In the original algo as described in PTP paper, Phase2b/Receive BeginBallot, an acceptor responded only if b=maxBal.
Hence, that means that the acceptors that responded would have voted in Phase1b, else b!=maxBal. Also therefore, Phase2b
did not need to update its notion of maxBal.

In the TLA+ spec, in Phase2b, an acceptor responds whenever b>=maxBal. Thus, it can respond even if it never went
through Phase1b for b. This requires the acceptor to save maxBal=b in Phase2b before responding.

This implementation contains the enhancement from the TLA+ algo.

The main classes are as given below.

* PaxosParticipant - a base type representing a participant
  * ThisPaxosParticipant - the participant inhabiting current process - the main class that implements the multi-paxos
    protocol
  * RemotePaxosParticipant - a remote participant with whom ThisPaxosParticipant communicates via messaging
* MultiPaxosProcess - a server process that runs the basic paxos
* MultiPaxosClient - a simple client

The Ledger is defined in the `ledger` project. All Decree values are of long type, to keep the implementation simple.
