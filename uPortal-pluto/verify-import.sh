#!/usr/bin/env bash
#
# Licensed to Apereo under one or more contributor license
# agreements. See the NOTICE file distributed with this work
# for additional information regarding copyright ownership.
# Apereo licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file
# except in compliance with the License.  You may obtain a
# copy of the License at the following location:
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Checks uPortal-pluto against the -sources jars it was imported from (see README.md): downloads
# each jar from Maven Central, checks its SHA-256, and lists every difference between the jar and
# the module's src/main/java + src/main/resources, apart from the Maven build files the import
# leaves out (META-INF/MANIFEST.MF, META-INF/DEPENDENCIES and portlet-api's META-INF/NOTICE.vm).
# It prints nothing and exits 0 while the code is unchanged; afterwards it lists exactly what
# uPortal changed.
#
# usage: uPortal-pluto/verify-import.sh [maven repository URL]
set -euo pipefail

repo=${1:-https://repo1.maven.org/maven2}
here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# module | path of the -sources jar in the repository | SHA-256 of that jar
artifacts='
uPortal-pluto-portlet-api|org/apache/portals/portlet-api_2.1.0_spec/1.0/portlet-api_2.1.0_spec-1.0-sources.jar|139b4e6d1cb944553a06dca2119566f13cd2dcc09282bce54ce9041a2b451d1b
uPortal-pluto-container-api|org/apache/portals/pluto/pluto-container-api/2.1.0-M3/pluto-container-api-2.1.0-M3-sources.jar|b5d91c25c0bab73f428fec172dd9dff74b40659828ba9358c9fa53b96e0508c9
uPortal-pluto-container-driver-api|org/apache/portals/pluto/pluto-container-driver-api/2.1.0-M3/pluto-container-driver-api-2.1.0-M3-sources.jar|521de17a87268750e8bff2b3c3135a8a2cb7e06f194eb8504f4ded4e880105a2
uPortal-pluto-container|org/apache/portals/pluto/pluto-container/2.1.0-M3/pluto-container-2.1.0-M3-sources.jar|94a6c6768e6537e845e5403922763cff359f3454d36e61b4075b5e953e1d8301
uPortal-pluto-portal-driver|org/apache/portals/pluto/pluto-portal-driver/2.1.0-M3/pluto-portal-driver-2.1.0-M3-sources.jar|fa921c32fa1ae7ef06691ed01f4aab400f03c5717986badf444ba48bc6a08986
uPortal-pluto-taglib|org/apache/portals/pluto/pluto-taglib/2.1.0-M3/pluto-taglib-2.1.0-M3-sources.jar|7ec3161dc7f1b5699ef97b9ac00e46c1f13e5d4d6229bca96717c20783ad09a7
'

status=0
while IFS='|' read -r module path sha256; do
    [ -n "$module" ] || continue
    jar="$work/$module.jar"
    curl -sSf --retry 3 -o "$jar" "$repo/$path"
    echo "$sha256  $jar" | sha256sum -c --quiet - || { echo "$module: checksum mismatch for $path" >&2; exit 1; }
    mkdir "$work/$module" "$work/$module-tree"
    unzip -q "$jar" -d "$work/$module"
    cp -R "$here/$module/src/main/java/." "$here/$module/src/main/resources/." "$work/$module-tree/"
    if ! diff -r -x MANIFEST.MF -x DEPENDENCIES -x NOTICE.vm "$work/$module" "$work/$module-tree" | sed "s|$work/||g"; then
        status=1
    fi
done <<< "$artifacts"
exit $status
