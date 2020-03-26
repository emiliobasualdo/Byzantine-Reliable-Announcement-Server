# Requirements
- Java 11
- Maven

# Current protocol (26/03/2020)
The http body is sent from client to server with its parameters \n separated.
```
HttpBody = signature \n Espub(action \n Cpub \n ..)
Signature = hash of Espub(action \n Cpub \n ..) signed with users private key
Espub = encryp with server's public key
Cpub = client's public key
```

# Run
### 0. Key's path
Determine a local path where to place store keys.  
Ex: `/Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/keys/`
### 1. Generate keys
`cd local/path/to/save/keys`  
Generate server's private key
```
keytool -genkeypair -alias serverKeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore server_keystore.p12 -storepass pass1234
```
Export server's keystore
```
keytool -exportcert -alias serverKeyPair -storetype PKCS12 \
  -keystore server_keystore.p12 -file \
  server_certificate.cer -rfc -storepass pass1234
```
Generate client's key
```
keytool -genkeypair -alias client1KeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore client1_keystore.p12 -storepass pass1234
```
Export client's keystore
```
keytool -exportcert -alias client1KeyPair -storetype PKCS12 \
  -keystore client1_keystore.p12 -file \
  client1_certificate.cer -rfc -storepass pass1234
```
### 2. Start client or server
Pass the key `local/path/to/save/keys` as first argument to the `main` method of either java files
