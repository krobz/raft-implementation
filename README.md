# raft-java
Raft implementation library for Java.  
Refer and optimize based on (https://github.com/wenweihu86/raft-java)。

# Supported Features
- Leader election
- Log replication
- Snapshot
- Dynamic cluster membership changes

## Quick Start
To deploy a 3-instance Raft cluster locally, execute the following script:  
cd raft-java-example && sh deploy.sh

This script will deploy three instances (`example1`, `example2`, `example3`) under the `raft-java-example/env` directory.  
It will also create a `client` directory for testing the read/write functionality of the Raft cluster.

After successful deployment, test the write operation with the following script:
cd env/client
./bin/run_client.sh "list://127.0.0.1:8051,127.0.0.1:8052,127.0.0.1:8053" hello world

To test the read operation:
./bin/run_client.sh "list://127.0.0.1:8051,127.0.0.1:8052,127.0.0.1:8053" hello


To test by HTTP:
cd raft/web-client/bin && sh deploy.sh
POST 192.168.227.117:8080/raft/write?key=project&value=raft
GET 192.168.227.117:8080/raft/read?key=project


## Usage
Below is a guide to using the `raft-java` dependency to implement a distributed storage system.

### Add Dependency
*Note: This dependency is not yet published to Maven Central and needs to be manually installed locally.*
<dependency>
<groupId>com.github.raftimpl.raft</groupId>
<artifactId>raft-java-core</artifactId>
<version>1.9.0</version>
</dependency>

### Define Data Write and Read Interfaces
#### Protobuf:
message SetRequest {
string key = 1;
string value = 2;
}
message SetResponse {
bool success = 1;
}
message GetRequest {
string key = 1;
}
message GetResponse {
string value = 1;
}

#### Java:
public interface ExampleService {
Example.SetResponse set(Example.SetRequest request);
Example.GetResponse get(Example.GetRequest request);
}

## Server Usage

### 1. Implement the `StateMachine` Interface
// This interface's three methods are mainly called by Raft internally
public interface StateMachine {
/**
* Take a snapshot of the data in the state machine, called periodically by each node.
* @param snapshotDir Directory to output snapshot data.
*/
void writeSnapshot(String snapshotDir);

    /**
     * Load a snapshot into the state machine, called when a node starts.
     * @param snapshotDir Directory containing snapshot data.
     */
    void readSnapshot(String snapshotDir);

    /**
     * Apply data to the state machine.
     * @param dataBytes Binary data.
     */
    void apply(byte[] dataBytes);
}

### 2. Implement Data Write and Read Interfaces
```
// The ExampleService implementation class must include the following members
private RaftNode raftNode;
private ExampleStateMachine stateMachine;

// Main logic for data write
byte[] data = request.toByteArray();
// Synchronously replicate data to the Raft cluster
boolean success = raftNode.replicate(data, Raft.EntryType.ENTRY_TYPE_DATA);
Example.SetResponse response = Example.SetResponse.newBuilder().setSuccess(success).build();

// Main logic for data read, implemented by the application's state machine
Example.GetResponse response = stateMachine.get(request);
```


### 3. Server Startup Logic
```
// Initialize RPCServer
RPCServer server = new RPCServer(localServer.getEndPoint().getPort());

// Application state machine
ExampleStateMachine stateMachine = new ExampleStateMachine();

// Configure Raft options, e.g.:
RaftOptions.snapshotMinLogSize = 10 * 1024;
RaftOptions.snapshotPeriodSeconds = 30;
RaftOptions.maxSegmentFileSize = 1024 * 1024;

// Initialize RaftNode
RaftNode raftNode = new RaftNode(serverList, localServer, stateMachine);

// Register Raft's internal service for node-to-node communication
RaftConsensusService raftConsensusService = new RaftConsensusServiceImpl(raftNode);
server.registerService(raftConsensusService);

// Register Raft service for client communication
RaftClientService raftClientService = new RaftClientServiceImpl(raftNode);
server.registerService(raftClientService);

// Register the application's service
ExampleService exampleService = new ExampleServiceImpl(raftNode, stateMachine);
server.registerService(exampleService);

// Start the RPCServer and initialize the Raft node
server.start();
raftNode.init();
```
