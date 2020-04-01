# Requirements
- Java 11
- Maven

# Run
### 0. Keys path
Determine a local path where to save the server keys.  
Ex: `/Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/keys/`
### 1. Generate server keystore
```shell script
mkdir -p /local/path/to/save/keys
cd /local/path/to/save/keys
keytool -genkeypair -alias serverKeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore server_keystore.p12 -storepass pass1234
```
### 2. Build
```
cd local/path/to/Dependable-Public-Announcement-Server 
mvn clean install
```
### 3. Start the server
Now you have to pass the path where the server_keystore was saved, the key alias and the password
```
java -jar server/target/server-1.0-jar-with-dependencies.jar  /Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/keys/server_keystore.p12  serverKeyPair pass1234
```
### 4. Start the client
```
java -jar client/target/client-1.0-jar-with-dependencies.jar  /Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/keys/server_keystore.p12  serverKeyPair pass1234
```

# Run maven tests
This runs simple tests we use to develop. For full testing of the server play around with the client.  
This tests are use check that the server returns correct or error messages when required.
```
mvn test
```

