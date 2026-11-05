# Development standards

- Do not add unnecessary information to the README.md file: implementation details do not belong in the README.md file.

- Keep it simple, stupid. Code should be easy to read and understand. Avoid complex structures and unnecessary abstractions.

- Use clear and descriptive names for variables, functions, and classes. Avoid abbreviations and single-letter names.

- Only write comments where strictly necessary and remember that comments should explain why something is done, not what is done.

- Prefer early guards in the code to reduce indentation.

- Always respect the .editorconfig configuration. Format Kotlin with ktlint using the `intellij_idea` code style.

- Run Entanglement against a baseline from time to time and refactor afterwards: every edited file must stay green (maintainability index of at least 20).

# Minecraft and gRPC standards

- Target the Minecraft version in `gradle.properties` (unobfuscated Mojang names). Verify game and Fabric API signatures against the actual jars instead of relying on memory.

- Access game state only on the server thread, through `GameServer.run`.

- Keep gRPC service implementations thin: they delegate to topical files in the same service package, which hold the game logic.

- Report failures as meaningful gRPC status codes (`INVALID_ARGUMENT`, `NOT_FOUND`, `FAILED_PRECONDITION`, ...), never as `INTERNAL` for expected errors.

- Keep the protobuf API backwards compatible: never renumber or reuse field numbers.

- Keep `buf lint` passing with the rules in `buf.yaml`.

# Testing standards

- Test gameplay with the HeadlessMC Docker setup in `compose.yaml` after `./gradlew build`. HeadlessMC must always use an offline account.

# Git standards

- Use Conventional Commits.

- Keep commit messages concise and spot on; do not overexplain.

- Each commit should represent a single, logical change to the codebase. Avoid bundling multiple unrelated changes in a single commit.
