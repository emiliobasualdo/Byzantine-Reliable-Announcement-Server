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
Pass the path where the `server_keystore` was saved, the key alias, the password, the server ip and port
```shell script
java -jar client/target/client-1.0-jar-with-dependencies.jar /local/path/to/save/keys/server_keystore.p12 serverKeyPair pass1234 127.0.0.1 8000
```

# Run a hacker
To simulate a hacker you can start a proxy server that can read, drop, edit and duplicate the packages sent between server and client  
1. Start the server on port X
2. Start the hacker on port Y
3. Start the client and tell him the server is at Y

### Start the hacker
Pass the port where the hacker will start, the ip and the port the server is listening to
```shell script
java -jar hacker/target/hacker-1.0-jar-with-dependencies.jar 8001 127.0.0.1 8000
```

# Run maven tests
This runs simple tests we use to develop. For full testing of the server play around with the client.  
This tests are use check that the server returns correct or error messages when required.
```shell script
mvn test
```

