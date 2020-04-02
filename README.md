# Requirements
- Java 11
- Maven

# Run
### 0. Keys path
Determine a local path where to save the server keys.  
Ex: `/local/path/to/save/keys`

### 1. Generate server keystore
```shell script
mkdir -p /local/path/to/save/keys
cd /local/path/to/save/keys
keytool -genkeypair -alias serverKeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore server_keystore.p12 -storepass pass1234
```

### 2. Build
```shell script
cd local/path/to/Dependable-Public-Announcement-Server 
mvn clean install
```

### 3. Start the server
Pass the path where the `server_keystore` was saved, the key alias, the password, the ip and the port the server will listen to
```shell script
java -jar server/target/server-1.0-jar-with-dependencies.jar /local/path/to/save/keys/server_keystore.p12 serverKeyPair pass1234 127.0.0.1 8000
```

### 4. Start the client
The client will generate its own keypair by default, therefore a "new" client is generated on every run of the following jar. 
If you intend on using the same client more than once. Go to section 4.2
##### 4.1 New client  
Pass the path where the `server_keystore` was saved, the key alias, the password, the server ip and port
```shell script
java -jar client/target/client-1.0-jar-with-dependencies.jar /local/path/to/save/keys/server_keystore.p12 serverKeyPair pass1234 127.0.0.1 8000
```

##### 4.2 Reuse client
- Generate the client's key
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
Pass the path where the `server_keystore` was saved, the key alias, the password, the server ip and port, the path where the `client_keystore` was saved, the key alias and the password
```shell script
java -jar client/target/client-1.0-jar-with-dependencies.jar /local/path/to/save/keys/server_keystore.p12 serverKeyPair pass1234 127.0.0.1 8000 /local/path/to/save/keys/client_keystore.p12 clientKeyPair pass1234
```

# Run a hacker
To simulate a hacker you can start a proxy server that can read, drop, edit and duplicate the messages sent between the server and the client  
1. Start the server on port X
2. Start the hacker on port Y
3. Start the client and tell him the server is listening on port Y

### Start the hacker
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

