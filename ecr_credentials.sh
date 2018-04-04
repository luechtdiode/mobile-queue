#!/usr/bin/env bash

mkdir -p ~/.aws

cat > ~/.aws/credentials << EOL
[default]
aws_default_region = eu-west-1
aws_access_key_id = ${aws_user}
aws_secret_access_key = ${aws_accesskey}
EOL