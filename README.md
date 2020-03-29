# Requirements
- Java 11
- Maven

# Current protocol (26/03/2020)
The JSON body is sent from the client to the server using POST requests.
```http request
POST http://localhost:8001/twitter
Content-Type: application/json

{
  "client_public_key": "",
  "number": 1,
  "signature": "",
  "action": "READ",
  "board_public_key": ""
}
```

# Run
### 0. Keys path
Determine a local path where are located keystores.  
Ex: `/Users/pilo/development/ist/hds/Dependable-Public-Announcement-Server/keys/`
### 1. Generate keys
```shell script
mkdir -p /local/path/to/save/keys
cd /local/path/to/save/keys
```
Generate server's private key
```shell script
keytool -genkeypair -alias serverKeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore server_keystore.p12 -storepass pass1234
```
Export server's keystore
```shell script
keytool -exportcert -alias serverKeyPair -storetype PKCS12 \
  -keystore server_keystore.p12 -file \
  server_certificate.cer -rfc -storepass pass1234
```
Generate client's key
```shell script
keytool -genkeypair -alias client1KeyPair -keyalg RSA -keysize 2048 \
  -dname "CN=twitter" -validity 365 -storetype PKCS12 \
  -keystore client1_keystore.p12 -storepass pass1234
```
Export client's keystore
```shell script
keytool -exportcert -alias client1KeyPair -storetype PKCS12 \
  -keystore client1_keystore.p12 -file \
  client1_certificate.cer -rfc -storepass pass1234
```
### 2. Start the client or the server
Pass the key path `/local/path/to/save/keys/` as first argument to the `main` method of either java files.
