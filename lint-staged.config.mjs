// Spotless formats the whole project, including the files that bun fmt handles, so the task takes
// no file names. lint-staged adds the formatted versions of the staged files to the commit.
// Windows runs the Gradle wrapper's batch file, which only cmd can start.
const spotlessApply =
    process.platform === "win32" ? "cmd /c gradlew.bat spotlessApply" : "./gradlew spotlessApply";

export default {
    "*": () => spotlessApply,
};
