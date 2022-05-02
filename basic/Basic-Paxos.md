# Basic Paxos

## Introduction

This is my attempt to describe the basic paxos algorithm in a programmer friendly way. I try to merge the description in
Leslie Lamport's [The Part-Time Parliament](http://lamport.azurewebsites.net/pubs/lamport-paxos.pdf) paper and subsequent [TLA+ specification](https://github.com/tlaplus/Examples/blob/master/specifications/PaxosHowToWinATuringAward/Paxos.pdf).

## Terms

* `Process` - a Paxos participant (priest in PTP paper).
* `Ballot` - a referendum on a single decree. Each Ballot is identified by a unique ballot number, and Ballots are ordered by the ballot number. 
* `Decree` - represents the value being agreed upon, i.e. the value being voted on. 
* `Ballot Number` - a unique number made up of a pair - proposal number, and process id. Ballot Numbers are sorted by proposal number, followed by process id.
* `Process id` - a unique id given to each process.
* `Proposal number` - a monotonically increasing sequence number maintained by each process, `-1` indicates none, valid values are `>= 0`.
* `Vote v` - a vote cast by a process, is a tuple containing the process id of the voter, the ballot number, and the decree being voted. Votes are ordered by ballot numbers.
* `Ledger` - each process must maintain some data in persistent storage - the ledger represents the storage data structure.
* `quorumSize` - `(Number of Participants + 1) / 2`, where Number of participants is an odd number.

## Functions

* `MaxVote(votes)` - function that returns the max vote cast, where `max()` is based on the ordering of votes by ballot number.
* `owner(b)` - returns the process id that initiated the ballot number `b`.

## Data Maintained in the Ledger

* `outcome` - this is value of the `decree` in the ledger, or NULL if there is nothing written yet.
* `lastTried` - The ballot number that the process `p` last tried to initiate, or `(-1,p.id)` if none.
* `maxBal` - The maximum ballot number that process `p` ever agreed to participate in, or `(-1,p.id)` if `p` has never agreed to participate in a ballot.
* `maxVBal` - The ballot number in which `p` last voted or `(-1,p.id)` if `p` never voted.
* `maxVal` - The value of the decree associated with `maxVBal`, i.e. the decree that `p` last voted, or NULL if `p` never voted.

## Data Maintained by a Process p in memory

* `status` - the status of process `p`, which can be one of the following:
  * `idle` - not conducting or trying to begin a ballot
  * `trying` - trying to begin ballot number `ledger.lastTried`
  * `polling` - Conducting ballot number `ledger.lastTried`
  * On startup the status is assumed to be `idle`.

* `prevVotes` - the set of votes received in `LastVote` messages for the current ballot (i.e. ballot number in `ledger.lastTried`).
* `quorum` - the set of processes including `p`, that responded with `LastVote` messages for current ballot, only meaningful when `status == polling`.
* `voters` - the set of processes including `p`, from whom `p` has received `Voted` messages in the current ballot, only meaningful when `status == polling`.
* `decree` - if `status == polling`, then the decree of the current ballot, otherwise meaningless.

## Messages 

* `NextBallot` - aka PREPARE 1a - message sent by the ballot conductor.
* `LastVote` - aka PROMISE 1b - message sent by participant to ballot conductor.
* `BeginBallot` - aka ACCEPT 2a - messages sent by the ballot conductor.
* `Voted` - aka ACCEPTED 2b - message sent by participant to ballot conductor.
* `Success` - message sent by ballot conductor to all processes once the ballot is successfully completed.

The content and timing of each message is described below.

## Algorithm for Basic Paxos.

The following algorithm must be implemented for each process that is participating in a Basic Paxos run.

### Start New Ballot (Phase 1)

This step is invoked when a new ballot must be started, perhaps on client request.

* If `status` != `idle`, reject request. If `status` != `idle`, `p` is already conducting a ballot.
* Let `b = ledger.lastTried + 1`, where `1` is added to the proposal number. First valid proposal number is thus `0`, as initial value of `ledger.lastTried` is `(-1, p.id)`.
* Set `ledger.lastTried` to `b`.
* Set `p.status` to `trying`.
* Set `p.prevVotes` to empty set.
* To each process participating in basic paxos, send `NextBallot(ledger.lastTried)` PREPARE 1a message, including to itself.

### Receive `NextBallot(b)` PREPARE 1a, conditionally send `LastVote` PROMISE 1b

This is executed by each process that receives the `NextBallot` message.

* Let `b` = `NextBallot.b`.
* if `b > ledger.maxBal` then
  * Set `ledger.maxBal` to `b`
  * To the sender of ballot `b`, i.e. `owner(b)`, send `LastVote` (PROMISE 1b) message with following contents
    * `voter` - `p.id` (i.e. the id of the process sending the `LastVote`)
    * `v` Vote containing
      * `b` - ballot number
      * `ledger.maxVBal` 
      * `ledger.maxVal`

### Receive `LastVote(owner,b,v)` PROMISE 1b message

* if `b == ledger.lastTried` and `status == trying` then
  * Set add vote `v` to the set `prevVotes`. Note that two votes that are from different participants (i.e. `LastVote.voter` is different), must be considered as distinct votes here.
  * If count of `prevVotes` with ballot `b` is `>=` to `quorumSize` then start polling.

### Start Polling (Phase 2) - Send `BeginBallot(b,decree)` ACCEPT 2a 

This step is enabled when `status=trying` and there is a quorum of votes in `prevVotes` as described above.

* Set `status` to `polling`
* Set `quorum` to the set of processes in `prevVotes` where `v.b=ledger.lastTried`
* Set `voters` to the empty set.
* Let `maxVote = MaxVote(prevVotes)`
* If `maxVote` has a ballot `b` with proposal number `-1`, then set `decree` to the value requested by client.
* Else if `maxVote` has a ballot `b` with proposal number `>=0` then set `decree` to  `maxVote.decree`.
* Send `BeginBallot(b,decree)` ACCEPT 2a to all the participants, including itself.

### Receive `BeginBallot(b, decree)` ACCEPT 2a message, conditionally send `Voted` ACCEPTED 2b 

This is executed by each process that receives the `BeginBallot` message.

* if `BeginBallot.b >= ledger.maxBal`
  * Set `ledger.maxBal` to `BeginBallot.b`
  * Set `ledger.maxVBal` to `BeginBallot.b`
  * Set `ledger.maxVal` to `BeginBallot.decree`
  * Send to the owner of ballot `owner(BeginBallot.b)`, a `Voted(b, id)` ACCEPTED 2b message where `b` is `ledger.maxBal`, and `id` is the process sending the `Voted` message.

### Receive `Voted(b,voter)` ACCEPTED 2b message

This step is enabled when `status=polling`.

* if `Voted.b == ledger.lastTried` and `status == polling`
  * Add voter process `Voted.voter` to the set of `voters`.
  * if count of `voters` is `>=` to `quorumSize` then
    * If `ledger.outcome` is NULL then set `ledger.outcome` to `decree`
    * Send `Success(ledger.outcome)` to all participants, including itself
    * Set `status` to `idle`

### Receive `Success(outcome)` message

* If `ledger.outcome` is NULL then set `ledger.outcome` to `Success.outcome`

