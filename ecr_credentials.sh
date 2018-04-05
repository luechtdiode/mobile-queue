#!/usr/bin/env bash

# make script executable: git update-index --add --chmod=+x ecr_credentials.sh

mkdir -p ~/.aws

cat > ~/.aws/credentials << EOL
[default]
aws_default_region = eu-west-1
aws_access_key_id = ${aws_user}
aws_secret_access_key = ${aws_accesskey}
EOL

cat > client/mobile-ticket-queue/build.json << EOL
{
  "android": {
      "release": {
          "keystore": "~/requisites/mobile-ticket-queue.jks",
          "storePassword": "${keystorePassword}",
          "alias": "mobile-ticket-queue",
          "password" : "${keystorePassword}",
          "keystoreType": ""
      }
  }
}
EOL
