# Development standards

- Keep implementation details out of README.md.

- Keep the code simple and easy to read. Avoid complex structures and unnecessary abstractions.

- Use clear and descriptive names for variables, functions, and classes. Avoid abbreviations and single-letter names.

- Write comments only where they are needed. A comment explains why the code does something, not what it does.

- Prefer early guards in the code to reduce indentation.

- Follow `.editorconfig`.

- Run `./gradlew spotlessApply` as the last step before considering a task done.

- Run Entanglement against a baseline regularly and refactor based on its results. Every edited file must stay green, with a maintainability index of at least 20.

# Minecraft, MCP and agent standards

- Target the Minecraft version in `gradle.properties` (unobfuscated Mojang names). Verify game and Fabric API signatures against the actual jars instead of relying on memory.

- Access game state only on the server thread, through `GameServer.run`.

- Keep MCP tool definitions thin. They live next to the game logic in their service package and delegate to the files there, grouped by topic.

- Report expected failures with the `ConduitError` kind that fits them, such as `InvalidInput`, `NotFound` or `FailedPrecondition`. Only unexpected exceptions may end up as internal errors.

- Keep the MCP tools backwards compatible: their names, actions, arguments and output format. Mark every action as read, write or destructive, so approvals and MCP hosts can rely on the marks.

- Never log or show secrets: runner secrets, MCP tokens and CLI logins.

- Keep the runner in `agent/` free of dependencies. CLI flags belong in `agent/presets.mjs`, so a CLI change only needs a new image, not a new mod.

- The mod never starts or stops the agent containers. It only talks to their runners over HTTP.

# Testing standards

- Test gameplay with the HeadlessMC Docker setup in `compose.yaml` after `./gradlew build`. HeadlessMC must always use an offline account.

# Git standards

- Use Conventional Commits.

- Keep commit messages short and precise.

- Make each commit one logical change. Do not bundle unrelated changes into one commit.
