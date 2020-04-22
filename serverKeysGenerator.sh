serverCount=$2

for (( i=0; i < serverCount; i++ ))
do
  alias="server_$i"
 keytool -genkeypair \
 -alias $alias \
 -keyalg RSA -keysize 2048 \
  -dname "CN=pt.ulisboa.tecnico.hds" \
  -validity 365 \
  -storetype PKCS12 \
  -keystore "$1" \
  -storepass "$3"
done