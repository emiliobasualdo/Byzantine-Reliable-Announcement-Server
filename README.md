# Requirements
- Java 11
- Maven

# Run
### 0. Keys path
Determine a local path where to save the server keys.  
Ex: `/local/path/to/save/keys`

### 1. Build
```shell script
cd local/path/to/Dependable-Public-Announcement-Server 
mvn clean install
```

### 2. Generate servers keystore
We generate each server's keys by:
1. creating a folder were we place the keystore for all servers
2. creating a key for each one by passing the folder path and the number of servers to our bash helper 
```shell script
mkdir -p /local/path/to/save/keys
sh serverKeysGenerator.sh /local/path/to/save/keys/server_keystore.p12 numberOfServers pass1234
```

### 3. Start the server
We now use a client_config.txt file to pass all the required parameters to the client(servers keystore, keystore password, alias, ports, etc)  
Edit it to your need, you can find an example in the src of the entire project.  
Pass the path where the client_config.txt file the server number(ex: 1) and the port
```shell script
java -jar server/target/server-1.0-jar-with-dependencies.jar path/to/Dependable-Public-Announcement-Server/client_config.txt 1 8001
```

### 4. Start the client
The client will generate its own keypair by default, therefore a "new" client is generated on every run of the following jar. 
If you intend on using the same client more than once. Go to section 4.2
##### 4.1 New client    
The client_config.txt is the only parameter passed to the client.  
By default the client generates a new pair of symmetric keys on startup.
```shell script
java -jar server/target/server-1.0-jar-with-dependencies.jar /path/to/Dependable-Public-Announcement-Server/client_config.txt
```

##### 4.2 Reuse client's key
You can reuse a previous keypair by passing it as an argument on startup.
- Generate the client's key and save it in a keystore
```shell script
mkdir -p /local/path/to/save/keys
cd /local/path/to/save/keys
keytool -genkeypair -alias clientKeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore client_keystore.p12 -storepass pass1234
```
- Move to the source code directory
```shell script
cd local/path/to/Dependable-Public-Announcement-Server 
```
Edit the client_config.txt file and remove the # to uncomment the needed parametrs, replace the values with the ones you just used tot created the client's key
```shell script
java -jar server/target/server-1.0-jar-with-dependencies.jar /Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/client_config.txt
```

# Run a hacker
To simulate a hacker you can start a proxy server that can read, drop, edit and duplicate the messages sent between the server and the client  
1. Start the server on port X
2. Start the hacker on port Y
3. Start the client and tell him the server is listening on port Y

### Start the hacker
You could use thte hacker to simulate a server's presence.  
Pass the port where the hacker will start, the ip and the port the server is listening to
```shell script
java -jar hacker/target/hacker-1.0-jar-with-dependencies.jar 8001 127.0.0.1 8000
```

# Run Maven tests
This runs simple tests we use to develop. For full testing of the server play around with the client.  
These tests are used to check that the server returns correct or error messages when required.
```shell script
mvn test
```

