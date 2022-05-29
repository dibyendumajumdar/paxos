# paxos

The goal of this project is to implement a minimal example of Paxos Consensus algorithm.

For simplicity - we will attempt to create a replicated log where values stored are fixed length (64 bits long values only). 

## Modules

* utils - basic facilities
* net - NIO based simple network protocol
* ledger - The ledger as described in Lamport's paper
* basic - Basic Paxos implementation - allows consensus on a single value (decree)
* multi - Multi Paxos implementation - allows consensus on multiple values (decrees) - similar to replicated log

## Reading Material

The implementation of Paxos is based on the description provided by Leslie Lamport in the paper
[The Part-Time Parliament](http://lamport.azurewebsites.net/pubs/lamport-paxos.pdf). Some terminology
used stems from this paper, e.g. Ledger, Decree, Ballot, etc. The message definitions follow this paper
rather than the subsequent papers where Leslie used other terminology such as Phase 1a, etc.
Where possible these alternative terms are referred to in logging and in comments.

Some terms from the original paper have been amended to match the terminology used in [Leslie's TLA+ specification of Paxos](http://lamport.azurewebsites.net/tla/st-pete-lecture-exercises.zip)
. Also see video lectures
entitled [The Paxos Algorithm or How to Win a Turing Award](https://lamport.azurewebsites.net/tla/paxos-algorithm.html).

## Description of Paxos Algorithms

I attempt to describe the algorithms implemented in a programmer friendly way rather than using TLA+.

* [Basic Paxos](basic/Basic-Paxos.md)
* [Multi Paxos](multi/Multi-Paxos.md)

## Trying it out

Following instructions are for running a paxos group of 3 processes.

1. Clone the project and ensure you have JDK 11 or above installed.
2. Run the gradle build to create a jar as follows. From the root of the project:

```
./gradlew uberJar  
```

Above will create a jar file named `multi/build/libs/multi-uber.jar`

3. We need to assign a port number to each process, lets assume these are `9000`,`9001`,`9002`. We also assume that the
   processes are running on the same host.
4. We need a folder for the `ledger` files - lets assume this is created in `/tmp/logdata`.
5. Open a shell, and run following in first shell.

```
java -jar  multi/build/libs/multi-uber.jar --myid 0 --connections localhost:9000;localhost:9001;localhost:9002 --logpath /tmp/logdata
```

6. In a second shell run:

```
java -jar  multi/build/libs/multi-uber.jar --myid 1 --connections localhost:9000;localhost:9001;localhost:9002 --logpath /tmp/logdata
```

7. In a third shell run:

```
java -jar  multi/build/libs/multi-uber.jar --myid 2 --connections localhost:9000;localhost:9001;localhost:9002 --logpath /tmp/logdata
```

8. At this point all the three processes should be connected to each other.
9. You can now submit a request as follows to one of the processes. Open another shell and run:

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 100
```

The first argument is the port number, and second argument is the value to be agreed upon. Each time you submit a
request a new decree number will be assigned to the value.

## Example of a sequence

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 100
Sending request
Received back ClientResponseMessage{dnum=0, agreedValue=100}

java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 200
Sending request
Received back ClientResponseMessage{dnum=1, agreedValue=200}

java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 300
Sending request
Received back ClientResponseMessage{dnum=2, agreedValue=300}

java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 400
Sending request
Received back ClientResponseMessage{dnum=3, agreedValue=400}
```

We switch to process on port `9001`

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9001 500
Sending request
Received back ClientResponseMessage{dnum=4, agreedValue=500}
```

We switch to process on port `9002`

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9002 600
Sending request
Received back ClientResponseMessage{dnum=5, agreedValue=600}
```

At this point we killed process that was listening on `9001`.

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9001 700
May 29, 2022 12:32:43 PM org.redukti.paxos.net.impl.EventLoopImpl handleConnect
SEVERE: Error occurred when completing connection Connection={id=1}: Connection refused: no further information
```

So we try `9000`.

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 700
Sending request
Received back ClientResponseMessage{dnum=6, agreedValue=700}

java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9000 800
Sending request
Received back ClientResponseMessage{dnum=7, agreedValue=800}
```

We restarted process that was listening on `9001`, and contact this process for the next request.

```
java -cp multi/build/libs/multi-uber.jar org.redukti.paxos.multi.MultiPaxosClient 9001 900
Sending request
Received back ClientResponseMessage{dnum=8, agreedValue=900}
```

## LICENSE

MIT License