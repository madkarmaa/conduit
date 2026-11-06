# Development standards

- Keep implementation details out of README.md.

- Keep the code simple and easy to read. Avoid complex structures and unnecessary abstractions.

- Use clear and descriptive names for variables, functions, and classes. Avoid abbreviations and single-letter names.

- Write comments only where they are needed. A comment explains why the code does something, not what it does.

- Prefer early guards in the code to reduce indentation.

- Follow `.editorconfig`.

- Run `./gradlew spotlessApply` as the last step before considering a task done.

- Run Entanglement against a baseline regularly and refactor based on its results. Every edited file must stay green, with a maintainability index of at least 20.

# Minecraft and gRPC standards

- Target the Minecraft version in `gradle.properties` (unobfuscated Mojang names). Verify game and Fabric API signatures against the actual jars instead of relying on memory.

- Access game state only on the server thread, through `GameServer.run`.

- Keep gRPC service implementations thin. They delegate to files in the same service package, grouped by topic, which hold the game logic.

- Report expected failures with the gRPC status code that fits them, such as `INVALID_ARGUMENT`, `NOT_FOUND` or `FAILED_PRECONDITION`, never with `INTERNAL`.

- Keep the protobuf API backwards compatible. Never renumber or reuse field numbers.

- Keep `buf lint` passing with the rules in `buf.yaml`.

# Testing standards

- Test gameplay with the HeadlessMC Docker setup in `compose.yaml` after `./gradlew build`. HeadlessMC must always use an offline account.

# Git standards

- Use Conventional Commits.

- Keep commit messages short and precise.

- Make each commit one logical change. Do not bundle unrelated changes into one commit.
