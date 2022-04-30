This module contains an implementation of Basic Paxos.

The implementation is based on the description provided by Leslie Lamport in the paper 
[The Part-Time Parliament](http://lamport.azurewebsites.net/pubs/lamport-paxos.pdf). Some of the terminology
used stems from this paper, e.g. Ledger, Decree, Ballot, etc. The message definitions follow this paper
rather than the subsequent papers where Leslie used other terminology such as Phase 1a, etc.
Where possible these alternative terms are referred to in logging and in comments.

Some terms from the original paper have been amended to match the terminology used in [Leslie's TLA+ specification
of Paxos](http://lamport.azurewebsites.net/tla/st-pete-lecture-exercises.zip). Also see video
lectures entitled [The Paxos Algorithm or How to Win a Turing Award](https://lamport.azurewebsites.net/tla/paxos-algorithm.html).

The main changes from the Part-Time Parliament paper are as follows:

* `nextBal` renamed to `maxBal`
* `prevBal` renamed to `maxVBal`
* `prevDec` renamed to `maxVal`

I also noticed some changes in the way `nextBal` / `maxBal` is used - I decided to update the implementation
to use the later TLA+ spec.

There are also some differences in the description of the algo, I found following [discussion of these differences
on StackOverflow](https://stackoverflow.com/questions/29880949/contradiction-in-lamports-paxos-made-simple-paper) that 
seem helpful.

I decided to keep both the PTP algo and the TLA+ algo in the implementation.

The main classes are as given below.

* PaxosParticipant - a base type representing a participant
  * ThisPaxosParticipant - the participant inhabiting current process
  * RemotePaxosParticipant - a remote participant with whom ThisPaxosParticipant communicates via messaging
* BasicPaxosProcess - a server process that runs the basic paxos
* BasicPaxosClient - a simple client

The Ledger is defined in the `log` project.
All Decree values are of long type, to keep the implementation simple.
