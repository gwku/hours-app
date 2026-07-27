#!/usr/bin/env bash
#
# Creates the release signing key and puts it in Infisical, which syncs it on to
# the repository's GitHub secrets.
#
# Run this once, before handing out the first APK. The key that signs the first
# install has to sign every update after it: Android refuses an update signed
# with a different key, and the only way out is uninstalling, which takes the
# database with it.
#
# The passwords are yours. This script asks for them and never writes them to
# disk in the clear.

set -euo pipefail

PROJECT_ID="1274f309-4884-495a-9f2a-5aa2ee5e7a66"
ENVIRONMENT="prod"
KEY_ALIAS="hours"
# Lives in the app module: the signing config resolves this path
# relative to app/, not to the project root.
KEYSTORE="app/release-keystore.jks"

cd "$(dirname "$0")/.."

if [ -f "$KEYSTORE" ]; then
    echo "$KEYSTORE already exists, refusing to overwrite it."
    echo "If you really want a new key, move the old one somewhere safe first."
    exit 1
fi

echo "Creating $KEYSTORE with alias $KEY_ALIAS."
echo "Pick a long password and keep it somewhere you will still have in ten years."
# Same subject as the Garen key: name and country only, the rest left at the
# keytool default. Nothing here is verified by anyone; it only has to stay the
# same across releases.
keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Gerwin Kuijntjes, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=NL"

read -r -s -p "Keystore password (the one you just chose): " STORE_PASSWORD
echo
read -r -s -p "Key password (press enter if it is the same): " KEY_PASSWORD
echo
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

echo "Writing local keystore.properties…"
cat > keystore.properties <<EOF
storeFile=release-keystore.jks
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF

echo "Pushing secrets to Infisical…"
infisical secrets set \
    --projectId="$PROJECT_ID" --env="$ENVIRONMENT" \
    "KEYSTORE_BASE64=$(base64 -i "$KEYSTORE" | tr -d '\n')" \
    "KEYSTORE_PASSWORD=$STORE_PASSWORD" \
    "KEY_ALIAS=$KEY_ALIAS" \
    "KEY_PASSWORD=$KEY_PASSWORD"

echo
echo "Done. Infisical now holds everything needed to rebuild both files:"
echo "KEYSTORE_BASE64 is the keystore itself, and the other three are the"
echo "contents of keystore.properties. Both files stay git-ignored."
echo
echo "Keep a copy somewhere outside Infisical too. Not because anything is"
echo "missing there, but because a lost signing key cannot be replaced: every"
echo "future update has to be signed with this one, or the app has to be"
echo "uninstalled and reinstalled, taking its database with it."
