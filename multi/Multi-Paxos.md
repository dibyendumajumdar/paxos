# Multi Paxos

## Introduction

Status: WIP

This is my attempt to describe the multi paxos algorithm in a programmer friendly way. 

## Terms

* `Process (p)` - a Paxos participant (priest in PTP paper).
* `Ballot` - a referendum on a single decree. Each Ballot is identified by a unique ballot number, and Ballots are ordered by the ballot number. 
* `Decree` - represents the value being agreed upon. If we were replicating a log, then the decree would be the log record. 
* `Decree number (dnum)` - decrees are numbered from 0, and sequentially allocated, each `dnum` identifies a `decree`.
* `Process pid` - a unique id given to each process. First process gets `0`, second `1`, etc. The ordering of processes is not specified, however it must be a constant.
* `Proposal number (pnum)` - a monotonically increasing sequence number maintained by each process, `-1` indicates none, valid values are `>= 0`.
* `Ballot Number (b)` - a unique number made up of a pair - proposal number `pnum`, and process id `pid`. Ballot Numbers are sorted by proposal number, followed by process id.
* `Vote (v)` - a vote cast by a process, is a tuple containing the `pid` of the voter, the ballot number, and the decree being voted. Votes are ordered by ballot numbers.
* `Ledger` - each process must maintain some data in persistent storage - the ledger represents the storage data structure.
* `quorumSize` - `(Number of Participants + 1) / 2`, where Number of participants is an odd number.
* `cnum` - The last sequentially committed `dnum`, i.e. all `dnum`s <= `cnum` are also committed.

## Functions

* `MaxVote(votes)` - function that returns the max vote cast, where `max()` is based on the ordering of votes by ballot number.
* `owner(b)` - returns the process id that initiated the ballot number `b`.

## Data Maintained in the Ledger

* `outcome(dnum)` - this is value of the `decree` in the ledger for given `dnum`, or NULL if there is nothing written yet. When an outcome is saved, the ledger must check and update `cnum` to be the highest sequential `dnum` that is committed.
* `lastTried` - The ballot number that the process `p` last tried to initiate, or `(-1,p.id)` if none.
* `maxBal` - The maximum ballot number that process `p` ever agreed to participate in, or `(-1,p.id)` if `p` has never agreed to participate in a ballot.
* `maxVBal(dnum)` - For decree numbered `dnum`, the ballot number in which `p` last voted or `(-1,p.id)` if `p` never voted.
* `maxVal(dnum)` - For decree numbered `dnum`, the value of the decree associated with `maxVBal`, i.e. the decree that `p` last voted, or NULL if `p` never voted.
* `cnum` - Decree number of last sequential committed decree, all decrees with `dnum` <= `cnum` must have been committed

Notes: for a given `dnum`, the decree is initially undefined, then goes into `in-ballot` status, and finally into `committed` or `noop` status.
Thus `maxVal(dnum)` and `maxVBal(dnum)` only have meaning when the decree is in `in-ballot` status. 

## Data Maintained by a Process p in memory

* `status` - the status of process `p`, which can be one of the following:
  * `idle` - not conducting or trying to begin a ballot - aka FOLLOWING
  * `trying` - trying to begin ballot number `ledger.lastTried` - aka TRYING_TO_BE_LEADER
  * `polling` - Conducting ballot number `ledger.lastTried` - aka LEADING
  * On startup the status is assumed to be `idle`.

* `prevVotes[dnum]` - For each `dnum`, the set of votes received in `LastVote` messages for the current ballot (i.e. ballot number in `ledger.lastTried`).
* `prevVoters` - the set of voters who returned `LastVote` messages in response to `NextBallot`.
* `voters` - the set of processes including `p`, from whom the ballot conductor has received `Voted` messages in the current ballot, only meaningful when `status == polling`.
* `chosenValues[]` - if `status == polling`, then the set of decrees in the current ballot, otherwise meaningless.
* `chosenDnum` - if `status == polling`, then the `dnum` assigned to client decree assigned for current ballot, otherwise meaningless.

## Messages 

* `NextBallot` - aka PREPARE 1a - message sent by the ballot conductor.
* `LastVote` - aka PROMISE 1b - message sent by participant to ballot conductor.
* `BeginBallot` - aka ACCEPT 2a - messages sent by the ballot conductor.
* `PendingVote` - message sent by a process in response to `BeginBallot` that indicates willingness to vote, pending update of outcomes not known to the process. This triggers a resend of `BeginBallot` with outcomes for committed decrees not known to the voting process.
* `Voted` - aka ACCEPTED 2b - message sent by participant to ballot conductor in response to `BeginBallot`.
* `Success` - message sent by ballot conductor to all processes once the ballot is successfully completed.

The content and timing of each message is described below, except for `PendingVote` - the definition and purpose of each message is as per the PTP paper.
The `PendingVote` message is an additional message used to bring an process up-to-date before it responds with a `Voted` message.

## Algorithm for Multi Paxos.

The following algorithm must be implemented for each process that is participating in a Multi Paxos run.

### Start New Ballot (Phase 1)

This step is invoked when a new ballot must be started, perhaps on client request.

* If `p.status` == `idle`.
  * Let `b = ledger.lastTried + 1`, where `1` is added to the proposal number. First valid proposal number is thus `0`, as initial value of `ledger.lastTried` is `(-1, p.id)`.
  * Set `ledger.lastTried` to `b`.
  * Set `p.status` to `trying`.
  * Set `p.prevVotes` and `p.prevVoters` to empty set.
  * Set `p.chosenValues` to the empty set.
  * Set `p.chosenDnum` to `-1`. 
  * To each process participating in multi paxos, send `NextBallot(ledger.lastTried, p.pid, ledger.commitNum)` PREPARE 1a message, including to itself.
* Else If `ledger.maxBal == ledger.lastTried` and `p.status == polling` then and , i.e. process is already a leader
  * Set `p.chosenValues` to the empty set.
  * Set `p.chosenDnum` to `-1`. 
  * Jump to Start Polling (Phase 2).

PTP note: If process `p` has all decrees with numbers less than or equal to `commitnum` then he sends a `NextBallot(b,commitnum)` message in all instances of the 
Synod protocol for decree numbers larger than `commitnum`.

### Receive `NextBallot(b,pid,commitnum)` PREPARE 1a, conditionally send `LastVote` PROMISE 1b

This is executed by each process `q` that receives the `NextBallot` message.

* Let `b` = `NextBallot.b`.
* To the sender of ballot `b`, i.e. `owner(b)`, send `Success` message for all decrees with outcomes `(dnum,outcome)` from `q.ledger` where `dnum` > `NextBallot.commitnum`
* if `b > q.ledger.maxBal` then
  * Set `q.ledger.maxBal` to `b`
  * To the sender of ballot `b`, i.e. `owner(b)`, send `LastVote` (PROMISE 1b) message with following contents
    * all decrees that `q` voted in and whose outcome is not yet `committed` or `noop` status in  `q.ledger`:
      * `pid` - `q.id` (i.e. the id of the process sending the `LastVote`)
      * `b` - ballot number
      * `v[]` Votes containing
        * `dnum`
        * `ledger.maxVBal(dnum)` 
        * `ledger.maxVal(dnum)`
      * `commitNum` to `q.ledger.commitNum`.
    
In his response to the `NextBallot` message, `q` informs `p` of all decrees numbered greater than `commitnum` that already appear in q's ledger
(in addition to sending the usual `LastVote` information for decrees not in his ledger), and he asks `p` to send him any decrees numbered `commitnum` or
less that are not in his ledger.

### Receive `LastVote(pid,b,commitNum,v[])` PROMISE 1b message

* if `LastVote.commitnum < ledger.commitnum` then send `Success` message to `LastVote.owner` for all decrees with outcomes from `LastVote.commitnum+1` to `p.commitnum`.
* if `b == ledger.lastTried` and `p.status == trying` then
  * Add vote `v[]` to the set `prevVotes`. Note that two votes that are from different participants (i.e. `LastVote.voter` is different), must be considered as distinct votes here.
  * Add `pid` of voter to `prevVoters`
  * If count `prevVoters` >= to `quorumSize` then start polling.

### determine chosen values
* Let `maxVote = MaxVote(prevVotes)` for each `dnum`
* Let `new_dnum` = `-1`.
* If `maxVote[dnum]` has a ballot `b` with proposal number `-1`
*  If `dnum` != `max(dnum in prevVotes)` then set `decree[dnum]` to `noop`.
*  Else set `new_dnum` to `dnum`.
* Else if `maxVote` has a ballot `b` with proposal number `>=0` then set `decree[dnum]` to  `maxVote.decree[dnum]`.
* If `new_dnum` == `-1` then set `new_dnum` to the `max(dnum)+1`
* Set `decree[new_dnum]` to client provided decree


### Start Polling (Phase 2) - Send `BeginBallot(b,decree)` ACCEPT 2a 

This step is enabled when `status=trying` and there is a quorum of votes in `prevVotes[*]` as described above.

* Set `status` to `polling`
* Set `voters` to the empty set.
* determine chosen values
* Send `BeginBallot(b,pid,commitNum,chosenValues[])` ACCEPT 2a to all the participants, including itself.

### Receive `BeginBallot(pid,commitNum,chosenValues[])` ACCEPT 2a message, conditionally send `Voted` ACCEPTED 2b 

This is executed by each process that receives the `BeginBallot` message.

* if `BeginBallot.b >= ledger.maxBal`
  * Set `ledger.maxBal` to `BeginBallot.b`
  * for each decree in BeginBallot.decree
    * Set `ledger.maxVBal[dnum]` to `BeginBallot.b`
    * Set `ledger.maxVal[dnum]` to `BeginBallot.decree[dnum]`
  * Send to the owner of ballot `owner(BeginBallot.b)`, a `Voted(b, id)` ACCEPTED 2b message where `b` is `ledger.maxBal`, and `id` is the process sending the `Voted` message.

### Receive `Voted(b,voter)` ACCEPTED 2b message

This step is enabled when `status=polling`.

* if `Voted.b == ledger.lastTried` and `status == polling`
  * Add voter process `Voted.voter` to the set of `voters`.
  * if count of `voters` is `>=` to `quorumSize` then
    * If `ledger.outcome[dnum]` is NULL then set `ledger.outcome[dnum]` to `decree[dnum]` for all `dnum` in `decree`
    * Send `Success(ledger.outcome[])` for all `dnum` above to all participants, including itself
    * Set `status` to `idle`

### Receive `Success(outcomes[])` message

* If `ledger.outcome[dnum]` is NULL then set `ledger.outcome[dnum]` to `Success.outcome[dnum]`
* Set `ledger.commitnum` to max `dnum`

