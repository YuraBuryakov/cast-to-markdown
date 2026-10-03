# Releasing to Maven Central

Artifacts: `cast-to-markdown-parent` (POM), `cast-to-markdown-core`, `cast-to-markdown-pdf`, `cast-to-markdown-docx`,
each with sources and Javadoc jars and GPG signatures. The `release` profile in the parent POM builds all of them
and uploads them to the [Central Portal](https://central.sonatype.com/).

## One-time setup

1. **Central Portal account and namespace.** Sign in at <https://central.sonatype.com/> with the GitHub account
   `YuraBuryakov`. The namespace `io.github.yuraburyakov` is verified through GitHub; check that it shows as
   verified under *Namespaces*.
2. **Portal token.** *Account* > *Generate User Token*, then put it into `~/.m2/settings.xml`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

3. **GPG key** for signing, and publish its public part so that Central can check the signatures:

   ```bash
   gpg --full-generate-key                                   # RSA 4096 or ed25519, with a passphrase
   gpg --list-secret-keys --keyid-format long                # the key id is after "sec   rsa4096/"
   gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
   ```

   Keep a backup of the key: later releases must be signed with a key Central can find.

## Release

Example for `0.1.0`; the build must be green on `main`.

1. Set the version and the build timestamp (reproducible jars), then build and test:

   ```bash
   mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
   # pom.xml: <project.build.outputTimestamp> = the release time, e.g. 2026-10-10T12:00:00Z
   mvn verify
   ```

2. Commit and tag:

   ```bash
   git commit -am "Release 0.1.0"
   git tag -a v0.1.0 -m "0.1.0"
   ```

3. Build, sign and upload (GPG asks for the passphrase):

   ```bash
   mvn -Prelease deploy
   ```

   `autoPublish` is off: the upload is validated by Central and waits under *Deployments* in the Portal.
   Check the files there, then press **Publish**. Search on <https://central.sonatype.com/> finds it after a
   while; `repo.maven.apache.org` usually has it within half an hour.

4. Start the next version and push:

   ```bash
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false
   git commit -am "Start 0.2.0-SNAPSHOT"
   git push origin main --tags
   ```

A published version cannot be changed or deleted; a fix is a new version.

## Notes

- The `docx` build prints *"Required filename-based automodules detected: [commons-math3-3.6.1.jar]. Please don't
  publish this project to a public artifact repository!"*. The `requires commons.math3` is in Apache POI's own
  module descriptor (`org.apache.poi.poi`), not in ours, and POI itself is published with it; it does not block
  publishing `cast-to-markdown-docx`.
- The Javadoc of `core` also lists the package `...casttomarkdown.internal`: Javadoc counts its qualified
  export (`exports ... to` the format modules) as exported. Its package documentation says it is not API.
