## Notes

## Options for multi-paxos

### Run full basic protocol each decree.

Deciding on next decree number
* Assume client requests value to be appended, does not specify the decree number, instead expects server to inform the decree number once value is appended successfully
* Assume a server can only do one consensus run at any time, it queues up any requests until current run is completed or timed out
* Assume server can query ledger to find the maximum decree number that was committed

Steps

1. If server is already in a consensus client request is queued
2. If server is idle it starts the consensus run for the next client request
3. Server assigns the max committed decree number plus 1 to new value to be appended
4. Server starts consensus run for the decree number
5. If server finds value already assigned to the chosen decree number it updates max committed decree number, and restarts from 3.
6. If server finds no agreed value for the decree number, then it can proceed to try to reach consensus.
7. If consensus run times out, the server can restart new round for the same decree number

Changes

1. Need a client request message, response will be decree number assigned to the value
2. Server needs to have a queue of client requests
3. Server should start processing next client request only after current consensus run is completed

Potential Issues

* If the client submits same request to another server then the value could be appended multiple times (duplicated) with different decree numbers

