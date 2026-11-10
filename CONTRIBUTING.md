# Contributing to aether

Thanks for looking. A few things that make a change easy to accept.

## Reporting a problem

Open an issue and include:

- the exact `javac` / `java` command you ran
- the full stack trace, not the last line
- your JDK version (`java -version`)

## Sending a change

1. Fork, branch from `main`.
2. Keep the change focused — one concern per pull request.
3. Add or update a test under `src/test`. The suite runs on the plain JUnit
   console launcher, no build tool required.
4. Compile and run everything before opening the PR:

   ```bash
   javac -d build/main src/aether/*/*.java
   javac -cp "build/main:junit-standalone.jar" -d build/test src/test/*/*.java
   java -cp "build/main:build/test:junit-standalone.jar" \
        org.junit.platform.console.ConsoleLauncher --scan-class-path
   ```

## Code style

- JDK standard library only. No Maven, no Gradle, no third-party jars on the
  runtime classpath. That constraint is the point of the project.
- Public API takes interfaces, not concrete classes, where a caller might
  reasonably want to swap the implementation.
- Every public method that can throw documents what it throws and when.

## What gets asked in review

- Does the test fail without the fix?
- Is the new public surface actually needed, or can it stay package-private?
- Does the README's API table still match the code?
