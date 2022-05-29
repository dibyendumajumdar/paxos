# Multi Paxos

## Introduction

Status: WIP

This is my attempt to describe the multi paxos algorithm in a programmer friendly way. 

The multi-paxos algorithm is an extension of the basic-paxos algorithm described in [here](https://github.com/dibyendumajumdar/paxos/blob/main/basic/Basic-Paxos.md).

The key enhancements are as follows:

* Each process maintains multiple decrees in its ledger, and the highest consecutive committed decree number is tracked as `commitNum`. 
* During the protocol, each process passes its `commitNum` with the message - this allows the receiving process to inform the sender of any decrees committed greater than the sender's `commitNum`.
* When a new ballot is initiated, the conductor obtains agreement on all pending decrees, not just one of them. Pending decrees are those that are not yet committed but have been voted in some ballot.

In this version of multi-paxos there is no explicit leader election. Rather we assume that clients decide who to contact - and are sticky to a preferred process for a period of time, and the process that is contacted, tries to become leader if it is not already the leader.

The overall algorithm follows closely the description of multi-paxos in the PTP paper, but has the later enhancement that in phase 2, an acceptor can respond even if it has never participated in the ballot before. This requires the acceptor to catch-up on commits it doesn't know about, and we introduce a new message type `PendingVote` to support this requirement.

## Terms

* `Process (p)` - a Paxos participant (priest in PTP paper).
* `Ballot` - a referendum on a single decree. Each Ballot is identified by a unique ballot number, and ballots are ordered by the ballot number. 
* `Decree` - represents the value being agreed upon. If we were replicating a log, then the decree would be the log record. 
* `Decree number (dnum)` - decrees are numbered from 0, and sequentially allocated, each `dnum` identifies a `decree`.
* `Process pid` - a unique id given to each process. First process gets `0`, second `1`, etc. The ordering of processes is not specified, however it must be a constant.
* `Proposal number (pnum)` - a monotonically increasing sequence number maintained by each process, `-1` indicates none, valid values are >= `0`.
* `Ballot Number (b)` - a unique number made up of a pair - proposal number `pnum`, and process id `pid`. Ballot Numbers are sorted by proposal number, followed by process id.
* `Vote (v)` - a vote cast by a process, is a tuple containing the `pid` of the voter, the ballot number, and the decree being voted. Votes are ordered by ballot numbers.
* `Ledger` - each process must maintain some data in persistent storage - the ledger represents the storage data structure.
* `quorumSize` - `(Number of Participants + 1) / 2`, where Number of participants is an odd number.
* `commitNum` - The last sequentially committed `dnum`, i.e. all `dnum`s <= `commitNum` are also committed.

## Functions

* `MaxVote(votes)` - function that returns the max vote cast, where `max()` is based on the ordering of votes by ballot number.
* `owner(b)` - returns the process id that initiated the ballot number `b`.

## Data Maintained in the Ledger

* `outcome(dnum)` - this is value of the `decree` in the ledger for given `dnum`, or NULL if there is nothing written yet. When an outcome is saved, the ledger must check and update `commitNum` to be the highest sequential `dnum` that is committed.
* `lastTried` - The ballot number that the process `p` last tried to initiate, or `(-1,p.id)` if none.
* `maxBal` - The maximum ballot number that process `p` ever agreed to participate in, or `(-1,p.id)` if `p` has never agreed to participate in a ballot.
* `maxVBal(dnum)` - For decree numbered `dnum`, the ballot number in which `p` last voted or `(-1,p.id)` if `p` never voted.
* `maxVal(dnum)` - For decree numbered `dnum`, the value of the decree associated with `maxVBal`, i.e. the decree that `p` last voted, or NULL if `p` never voted.
* `commitNum` - Decree number of last sequential committed decree, all decrees with `dnum` <= `commitNum` must have been committed

Notes: for a given `dnum`, the decree is initially undefined, then goes into `in-ballot` status, and finally into `committed` or `noop` status.
Thus, `maxVal(dnum)` and `maxVBal(dnum)` only have meaning when the decree is in `in-ballot` status. 

## Data Maintained by a Process p in memory

* `status` - the status of process `p`, which can be one of the following:
  * `idle` - not conducting or trying to begin a ballot - aka FOLLOWING
  * `trying` - trying to begin ballot number `ledger.lastTried` - aka TRYING_TO_BE_LEADER
  * `polling` - Conducting ballot number `ledger.lastTried` - aka LEADING
  * On startup the status is assumed to be `idle`.
* `pid` - the process id
* `prevVotes[dnum]` - For each `dnum`, the set of votes received in `LastVote` (phase 1) messages for the current
  ballot (ballot number in `ledger.lastTried`).
* `prevVoters` - the set of voters (in phase 1) who returned `LastVote` messages in response to `NextBallot`.
* `voters` - the set of processes (in phase 2) including `p`, from whom the ballot conductor has received `Voted`
  messages in the current ballot, only meaningful when `status == polling`.
* `chosenValues[]` - if `status == polling`, then the set of decrees in the current ballot, otherwise meaningless.
* `chosenDnum` - if `status == polling`, then the `dnum` assigned to client decree assigned for current ballot,
  otherwise meaningless.

## Messages 

* `NextBallot` - aka PREPARE 1a - message sent by the ballot conductor.
* `LastVote` - aka PROMISE 1b - message sent by participant to ballot conductor.
* `BeginBallot` - aka ACCEPT 2a - messages sent by the ballot conductor.
* `PendingVote` - message sent by a process in response to `BeginBallot` that indicates willingness to vote, pending update of outcomes not known to the process. This triggers a resend of `BeginBallot` with outcomes for committed decrees not known to the voting process.
* `Voted` - aka ACCEPTED 2b - message sent by participant to ballot conductor in response to `BeginBallot`.
* `Success` - message sent by ballot conductor to all processes once the ballot is successfully completed.

The content and timing of each message is described below, except for `PendingVote` - the definition and purpose of each
message is as per the PTP paper. The `PendingVote` message is an additional message used to bring a process up-to-date
before it responds with a `Voted` message.

## Algorithm for Multi Paxos.

The following algorithm must be implemented for each process that is participating in a Multi Paxos run.

### P000 On Client Request

This step enabled by `p` if there are no client requests in process and a new request has arrived.

* If `p.status == idle` do `P101 Start New Ballot`.
* Else if `p.status == polling` and `ledger.maxBal == ledger.lastTried`, do `P200 Start New Vote`.

### P101 Start New Ballot (Phase 1)

This step is enabled when a new ballot must be started, e.g. on client request.

* If `p.status` == `idle`, i.e. process `p` is a follower so must try to be leader.
  * Let `b = Ballot(max(p.ledger.lastTried.pnum, p.ledger.maxBal.pnum) + 1, p.pid)`, where `1` is added to the proposal
    number. First valid proposal number is thus `0`, as initial value of `ledger.lastTried` and `ledger.maxBal`
    is `(-1, p.pid)`.
  * Set `p.ledger.lastTried` to `b`.
  * Set `p.status` to `trying`.
  * Set `p.prevVotes` and `p.prevVoters` to empty set.
  * Set `p.chosenValues` to the empty set.
  * Set `p.chosenDnum` to `-1`.
  * To each process participating in multi paxos, send `NextBallot(p.ledger.lastTried, p.pid, p.ledger.commitNum)`
    PREPARE 1a message, including to itself.

PTP note: If process `p` has all decrees with numbers less than or equal to `commitnum` then he sends
a `NextBallot(b,commitnum)` message in all instances of the Synod protocol for decree numbers larger than `commitnum`.

Any process receiving the `NextBallot` is required to inform `p` of any commits it is missing before responding with
a `LastVote` message.

### P200 Start New Vote (Phase 2)

* If `p.ledger.maxBal == p.ledger.lastTried` and `p.status == polling` then and , i.e. process is already a leader
  * Set `p.chosenValues` to the empty set.
  * Set `p.chosenDnum` to `-1`.
  * Jump to `P201 Start Polling` (Phase 2).

### P102 Receive `NextBallot(b,pid,commitnum)` PREPARE 1a, conditionally send `LastVote` PROMISE 1b

This is executed by each process `q` that receives the `NextBallot` message.

* Let `b` = `NextBallot.b`.
* To the sender of ballot `b`, i.e. `owner(b)`, send `Success` message for all decrees with outcomes `(dnum,outcome)`
  from `q.ledger` where `dnum` > `NextBallot.commitnum`. This step ensures that the ballot conductor is made aware of
  any commits it is missing.
* if `b > q.ledger.maxBal` then
  * Set `q.ledger.maxBal` to `b`
  * If `q` is not the owner of ballot `b` then at this point it needs to reset its state to `idle` as it is going to be
    a follower from here on.
  * To the sender of ballot `b`, i.e. `owner(b)`, send `LastVote` (PROMISE 1b) message with following contents
    * all decrees that `q` voted in and whose outcome is not yet `committed` in `q.ledger`:
      * `pid` - `q.id` (i.e. the id of the process sending the `LastVote`)
      * `b` - ballot number `q` is responding to
      * `v[]` Votes containing
        * `dnum`
        * `ledger.maxVBal(dnum)` - max ballot number `q` voted in
        * `ledger.maxVal(dnum)` - value associated with max ballot number above
      * `commitNum` to `q.ledger.commitNum`.

PTP note: In his response to the `NextBallot` message, `q` informs `p` of all decrees numbered greater than `commitnum`
that already appear in q's ledger
(in addition to sending the usual `LastVote` information for decrees not in his ledger), and he asks `p` to send him any
decrees numbered `commitnum` or less that are not in his ledger.

Note that the last bit is somewhat different, because the way we manage `commitnum` ensures that it is the highest
consecutive committed `dnum`, the invariant being that all decrees below `commitnum` already appear in `q`'s ledger at
this point. Instead, we send `commitnum`
so that `p` can inform us of any commits greater than our `commitnum`.

### P103 Receive `LastVote(pid,b,commitNum,votes[])` PROMISE 1b message

* if `LastVote.commitnum < p.ledger.commitnum` then send `Success` message to `LastVote.owner` for all decrees with
  outcomes from `LastVote.commitnum+1` to `p.commitnum`.
* if `b == ledger.lastTried` and `p.status == trying` then
  * Add `LastVote.votes[]` to the set `p.prevVotes[]`. Note that two votes that are from different participants (
    i.e. `LastVote.voter` is different), must be considered as distinct votes here.
  * Add `pid` of voter to `prevVoters`
  * If count `prevVoters` >= to `quorumSize` then do `P201 Start Polling`.

### P201a Determine chosen values

Goal is that all `dnum`s that are less than the max `dnum` in `LastVote` messages either get already voted values or
no-op if no value was voted. The client requested value is assigned a new `dnum` greater than all previous `dnum`s.

We need to ensure there are no gaps in `dnum`s.

* If `p.prevVotes` is not empty
  * For all `dnum` greater than `p.ledger.commitNum` and less or equal to `max(dnum)` in `p.prevVotes`,
    where `p.prevVotes[dnum]` is not an empty set, set `p.chosenValues[dnum]` = `MaxVote(p.prevVotes[dnum])`.
  * For all `dnum` greater than `p.ledger.commitNum` and less or equal to `max(dnum)` in `p.prevVotes`,
    where `p.prevVotes[dnum]` is an empty set, set `p.chosenValues[dnum]` = `no-op`.
* Set `p.chosenDnum` to `max(max(dnum in p.prevVotes), p.ledger.commitNum)+1`
* Set `p.chosenValues[chosenDnum]` to client provided decree

### P201 Start Polling (Phase 2) - Send `BeginBallot(b,decree)` ACCEPT 2a

This step is enabled when `status=trying` and there is a quorum of votes in `prevVotes[*]` as described above.

* Set `p.status` to `polling`
* Do `P201a Determine chosen values`
* Send `P202 BeginBallot(b,p.pid,p.commitNum,p.chosenValues,committedDecrees=[])` ACCEPT 2a to all the participants,
  including itself. Note `committedDecrees` is set to the empty set.

### P202 Receive `BeginBallot(b,pid,commitNum,chosenValues,committedDecrees)` ACCEPT 2a message, conditionally send `Voted` ACCEPTED 2b

This is executed by each process `q` that receives the `BeginBallot` message.

* If `BeginBallot.b >= ledger.maxBal`
  * Set `q.ledger.maxBal` to `BeginBallot.b`
  * for each decree in `BeginBallot.committedDecrees`
    * Set `q.ledger.outcome[dnum]` to `BeginBallot.committedDecrees[dnum]`
    * Advance `q.ledger.commitNum` to the highest consecutive committed `dnum`.
  * for each decree in `BeginBallot.chosenValues`
    * Set `q.ledger.maxVBal[dnum]` to `BeginBallot.b`
    * Set `q.ledger.maxVal[dnum]` to `BeginBallot.decree[dnum]`
  * If `q.ledger.commitNum` < `BeginBallot.commitNum`
    * Send to the owner of ballot `owner(BeginBallot.b)`, a `PendingVote(b, q.pid, q.ledger.commitNum)` PENDING ACCEPT
      2ba message where `q.pid` is the process sending the `PendingVote` message.
  * Else
    * Send to the owner of ballot `owner(BeginBallot.b)`, a `Voted(b, q.pid)` ACCEPTED 2b message where `q.pid` is the
      process sending the `Voted` message.

### P203 Receive `PendingVote(b,voter,commitNum)` PENDING ACCEPT 2ba message

This message means that the sender is willing to vote but is lagging behind in commits, and needs to update its commits
prior to voting. This step is enabled when `status=polling`.

* If `Voted.b == p.ledger.lastTried` and `p.status == polling`
  * Let `p.committedDecrees` be the set of decrees with `dnum` > `PendingVote.commitNum` that are in `p`'s ledger
  * Send `BeginBallot(b,p.pid,p.commitNum,p.chosenValues,p.committedDecrees)` ACCEPT 2a to `PendingVote.voter`.

### P204 Receive `Voted(b,voter)` ACCEPTED 2b message

This step is enabled when `status=polling`.

* if `Voted.b == p.ledger.lastTried` and `p.status == polling`
  * Add voter process `Voted.voter` to the set of `p.voters`.
  * If count of `p.voters` is `>=` to `quorumSize` then
    * For all `dnum` in `p.chosenValues` if `p.ledger.outcome[dnum]` is NULL then set `p.ledger.outcome[dnum]`
      to `p.chosenValues[dnum]`
    * Send `Success(p.chosenValues)` to all participants, including itself.

### P300 Receive `Success(outcomes[])` message

* For all `Success.outcomes[]`, if `p.ledger.outcome[dnum]` is NULL then set `p.ledger.outcome[dnum]`
  to `Success.outcome[dnum]`
* Advance `p.ledger.commitNum` to the highest consecutive committed `dnum`.

