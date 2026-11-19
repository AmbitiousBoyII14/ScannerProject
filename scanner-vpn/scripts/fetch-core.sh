#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p app/libs
file=app/libs/libv2ray.aar
hash=cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155
if [ ! -f "$file" ] || ! echo "$hash  $file" | sha256sum -c --status; then
  tmp=$(mktemp app/libs/download.XXXXXX); trap 'rm -f "$tmp"' EXIT
  curl -fL --retry 3 https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.11.9/libv2ray.aar -o "$tmp"
  echo "$hash  $tmp" | sha256sum -c - && mv "$tmp" "$file"
fi
