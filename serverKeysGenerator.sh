#!/bin/bash
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <keystore-path> <number-of-servers> <password>" >&2
  exit 1
fi

for ((i = 0; i < $2; i++)); do
  alias="server_$i"
  echo "Generating keys for $alias..."
  if ! keytool -genkeypair \
    -alias $alias \
    -keyalg RSA -keysize 2048 \
    -validity 365 \
    -storetype PKCS12 \
    -keystore "$1" \
    -storepass "$3"; then
    echo "Error generating keys for $alias, key generation cancelled" >&2
    exit 1
  fi
done

echo "Server keys successfully generated in $1"
